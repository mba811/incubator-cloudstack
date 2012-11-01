// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.network.guru;

import java.util.Random;

import javax.ejb.Local;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.dc.Pod;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapcityException;
import com.cloud.network.Network;
import com.cloud.network.NetworkProfile;
import com.cloud.network.NetworkVO;
import com.cloud.network.Networks.AddressFormat;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.Mode;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.StorageNetworkManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.user.Account;
import com.cloud.utils.Pair;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.component.Inject;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.Nic.ReservationStrategy;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

@Component
@Local(value={NetworkGuru.class})
public class PodBasedNetworkGuru extends AdapterBase implements NetworkGuru {
    private static final Logger s_logger = Logger.getLogger(PodBasedNetworkGuru.class);
    @Inject DataCenterDao _dcDao;
    @Inject StorageNetworkManager _sNwMgr;
    Random _rand = new Random(System.currentTimeMillis());
    
    private static final TrafficType[] _trafficTypes = {TrafficType.Management};
    
    @Override
    public boolean isMyTrafficType(TrafficType type) {
    	for (TrafficType t : _trafficTypes) {
    		if (t == type) {
    			return true;
    		}
    	}
    	return false;
    }

    @Override
    public TrafficType[] getSupportedTrafficType() {
    	return _trafficTypes;
    }
    
    @Override
    public Network design(NetworkOffering offering, DeploymentPlan plan, Network userSpecified, Account owner) {
        TrafficType type = offering.getTrafficType();
        
        if (!isMyTrafficType(type)) {
            return null;
        }
                        
        NetworkVO config = new NetworkVO(type, Mode.Static, BroadcastDomainType.Native, offering.getId(), Network.State.Setup, plan.getDataCenterId(), plan.getPhysicalNetworkId());
        return config;
    }
    
    protected PodBasedNetworkGuru() {
        super();
    }

    @Override
    public void deallocate(Network config, NicProfile nic, VirtualMachineProfile<? extends VirtualMachine> vm) {
    }
    
    @Override
    public NicProfile allocate(Network config, NicProfile nic, VirtualMachineProfile<? extends VirtualMachine> vm) throws InsufficientVirtualNetworkCapcityException,
            InsufficientAddressCapacityException {
        TrafficType trafficType = config.getTrafficType();
        assert trafficType == TrafficType.Management || trafficType == TrafficType.Storage: "Well, I can't take care of this config now can I? " + config; 
        
        if (nic != null) {
            if (nic.getRequestedIp() != null) {
                throw new CloudRuntimeException("Does not support custom ip allocation at this time: " + nic);
            }
            nic.setStrategy(nic.getIp4Address() != null ? ReservationStrategy.Create : ReservationStrategy.Start);
        } else {
            nic  = new NicProfile(ReservationStrategy.Start, null, null, null, null);
        } 
        
        return nic;
    }

    @Override
    public void reserve(NicProfile nic, Network config, VirtualMachineProfile<? extends VirtualMachine> vm, DeployDestination dest, ReservationContext context) throws InsufficientVirtualNetworkCapcityException,
            InsufficientAddressCapacityException {
        Pod pod = dest.getPod();
        
        Pair<String, Long> ip = _dcDao.allocatePrivateIpAddress(dest.getDataCenter().getId(), dest.getPod().getId(), nic.getId(), context.getReservationId());
        if (ip == null) {
            throw new InsufficientAddressCapacityException("Unable to get a management ip address", Pod.class, pod.getId());
        }
        
        nic.setIp4Address(ip.first());
        nic.setMacAddress(NetUtils.long2Mac(NetUtils.createSequenceBasedMacAddress(ip.second())));
        nic.setGateway(pod.getGateway());
        nic.setFormat(AddressFormat.Ip4);
        String netmask = NetUtils.getCidrNetmask(pod.getCidrSize());
        nic.setNetmask(netmask);
        nic.setBroadcastType(BroadcastDomainType.Native);
        nic.setBroadcastUri(null);
        nic.setIsolationUri(null);
        
        s_logger.debug("Allocated a nic " + nic + " for " + vm);
    }
    
    
    @Override
    public void updateNicProfile(NicProfile profile, Network network) {
    }
    
    @Override
    public void updateNetworkProfile(NetworkProfile networkProfile) {
    }
    
    @Override
    public boolean release(NicProfile nic, VirtualMachineProfile<? extends VirtualMachine> vm, String reservationId) {
        _dcDao.releasePrivateIpAddress(nic.getId(), nic.getReservationId());
        
        nic.deallocate();
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Released nic: " + nic);
        }
        
        return true;
    }

    @Override
    public Network implement(Network config, NetworkOffering offering, DeployDestination destination, ReservationContext context) throws InsufficientVirtualNetworkCapcityException {
        return config;
    }
    
    @Override
    public void shutdown(NetworkProfile config, NetworkOffering offering) {
    }
    
    @Override
    public boolean trash(Network config, NetworkOffering offering, Account owner) {
        return true;
    }
}
