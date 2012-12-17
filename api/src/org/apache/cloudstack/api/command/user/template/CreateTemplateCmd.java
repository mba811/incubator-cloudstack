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
package org.apache.cloudstack.api.command.user.template;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.IdentityMapper;
import org.apache.cloudstack.api.Implementation;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.StoragePoolResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import com.cloud.async.AsyncJob;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.projects.Project;
import com.cloud.storage.Snapshot;
import com.cloud.storage.Volume;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.UserContext;

@Implementation(responseObject = StoragePoolResponse.class, description = "Creates a template of a virtual machine. " + "The virtual machine must be in a STOPPED state. "
        + "A template created from this command is automatically designated as a private template visible to the account that created it.")
        public class CreateTemplateCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = Logger.getLogger(CreateTemplateCmd.class.getName());
    private static final String s_name = "createtemplateresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.BITS, type = CommandType.INTEGER, description = "32 or 64 bit")
    private Integer bits;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, required = true, description = "the display text of the template. This is usually used for display purposes.", length=4096)
    private String displayText;

    @Parameter(name = ApiConstants.IS_FEATURED, type = CommandType.BOOLEAN, description = "true if this template is a featured template, false otherwise")
    private Boolean featured;

    @Parameter(name = ApiConstants.IS_PUBLIC, type = CommandType.BOOLEAN, description = "true if this template is a public template, false otherwise")
    private Boolean publicTemplate;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the template")
    private String templateName;

    @IdentityMapper(entityTableName="guest_os")
    @Parameter(name = ApiConstants.OS_TYPE_ID, type = CommandType.LONG, required = true, description = "the ID of the OS Type that best represents the OS of this template.")
    private Long osTypeId;

    @Parameter(name = ApiConstants.PASSWORD_ENABLED, type = CommandType.BOOLEAN, description = "true if the template supports the password reset feature; default is false")
    private Boolean passwordEnabled;

    @Parameter(name = ApiConstants.REQUIRES_HVM, type = CommandType.BOOLEAN, description = "true if the template requres HVM, false otherwise")
    private Boolean requiresHvm;

    @IdentityMapper(entityTableName="snapshots")
    @Parameter(name = ApiConstants.SNAPSHOT_ID, type = CommandType.LONG, description = "the ID of the snapshot the template is being created from. Either this parameter, or volumeId has to be passed in")
    private Long snapshotId;

    @IdentityMapper(entityTableName="volumes")
    @Parameter(name = ApiConstants.VOLUME_ID, type = CommandType.LONG, description = "the ID of the disk volume the template is being created from. Either this parameter, or snapshotId has to be passed in")
    private Long volumeId;

    @IdentityMapper(entityTableName="vm_instance")
    @Parameter(name=ApiConstants.VIRTUAL_MACHINE_ID, type=CommandType.LONG, description="Optional, VM ID. If this presents, it is going to create a baremetal template for VM this ID refers to. This is only for VM whose hypervisor type is BareMetal")
    private Long vmId;

    @Parameter(name=ApiConstants.URL, type=CommandType.STRING, description="Optional, only for baremetal hypervisor. The directory name where template stored on CIFS server")
    private String url;

    @Parameter(name=ApiConstants.TEMPLATE_TAG, type=CommandType.STRING, description="the tag for this template.")
    private String templateTag;

    @Parameter(name=ApiConstants.DETAILS, type=CommandType.MAP, description="Template details in key/value pairs.")
    protected Map details;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public String getEntityTable() {
        return "vm_template";
    }

    public Integer getBits() {
        return bits;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Boolean isFeatured() {
        return featured;
    }

    public Boolean isPublic() {
        return publicTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }

    public Long getOsTypeId() {
        return osTypeId;
    }

    public Boolean isPasswordEnabled() {
        return passwordEnabled;
    }

    public Boolean getRequiresHvm() {
        return requiresHvm;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getVmId() {
        return vmId;
    }

    public String getUrl() {
        return url;
    }

    public String getTemplateTag() {
        return templateTag;
    }

    public Map getDetails() {
        if (details == null || details.isEmpty()) {
            return null;
        }

        Collection paramsCollection = details.values();
        Map params = (Map) (paramsCollection.toArray())[0];
        return params;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    public static String getResultObjectName() {
        return "template";
    }

    @Override
    public long getEntityOwnerId() {
        Long volumeId = getVolumeId();
        Long snapshotId = getSnapshotId();
        Long accountId = null;
        if (volumeId != null) {
            Volume volume = _entityMgr.findById(Volume.class, volumeId);
            if (volume != null) {
                accountId = volume.getAccountId();
            } else {
                throw new InvalidParameterValueException("Unable to find volume by id=" + volumeId);
            }
        } else {
            Snapshot snapshot = _entityMgr.findById(Snapshot.class, snapshotId);
            if (snapshot != null) {
                accountId = snapshot.getAccountId();
            } else {
                throw new InvalidParameterValueException("Unable to find snapshot by id=" + snapshotId);
            }
        }

        Account account = _accountService.getAccount(accountId);
        //Can create templates for enabled projects/accounts only
        if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
            Project project = _projectService.findByProjectAccountId(accountId);
            if (project.getState() != Project.State.Active) {
                PermissionDeniedException ex = new PermissionDeniedException("Can't add resources to the specified project id in state=" + project.getState() + " as it's no longer active");
                ex.addProxyObject(project, project.getId(), "projectId");
            }
        } else if (account.getState() == Account.State.disabled) {
            throw new PermissionDeniedException("The owner of template is disabled: " + account);
        }

        return accountId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_TEMPLATE_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating template: " + getTemplateName();
    }

    @Override
    public AsyncJob.Type getInstanceType() {
        return AsyncJob.Type.Template;
    }

    private boolean isBareMetal() {
        return (this.getVmId() != null && this.getUrl() != null);
    }

    @Override
    public void create() throws ResourceAllocationException {
        if (isBareMetal()) {
            _bareMetalVmService.createPrivateTemplateRecord(this, _accountService.getAccount(getEntityOwnerId()));
            /*Baremetal creates template record after taking image proceeded, use vmId as entity id here*/
            this.setEntityId(vmId);
        } else {
            VirtualMachineTemplate template = null;
            template = _userVmService.createPrivateTemplateRecord(this, _accountService.getAccount(getEntityOwnerId()));
            if (template != null) {
                this.setEntityId(template.getId());
            } else {
                throw new ServerApiException(BaseCmd.INTERNAL_ERROR,
                "Failed to create a template");
            }
        }
    }

    @Override
    public void execute() {
        UserContext.current().setEventDetails("Template Id: "+getEntityId()+((getSnapshotId() == null) ? " from volume Id: " + getVolumeId() : " from snapshot Id: " + getSnapshotId()));
        VirtualMachineTemplate template = null;
        if (isBareMetal()) {
            template = _bareMetalVmService.createPrivateTemplate(this);
        } else {
            template = _userVmService.createPrivateTemplate(this);
        }

        if (template != null){
            List<TemplateResponse> templateResponses;
            if (isBareMetal()) {
                templateResponses = _responseGenerator.createTemplateResponses(template.getId(), vmId);
            } else {
                templateResponses = _responseGenerator.createTemplateResponses(template.getId(), snapshotId, volumeId, false);
            }
            TemplateResponse response = new TemplateResponse();
            if (templateResponses != null && !templateResponses.isEmpty()) {
                response = templateResponses.get(0);
            }
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create private template");
        }

    }
}