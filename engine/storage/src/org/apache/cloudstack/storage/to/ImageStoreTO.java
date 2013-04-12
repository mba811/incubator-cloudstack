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
package org.apache.cloudstack.storage.to;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreRole;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreTO;
import org.apache.cloudstack.storage.image.datastore.ImageStoreInfo;

public class ImageStoreTO implements DataStoreTO {
    private final String type;
    private final String uri;
    private final String providerName;
    private final DataStoreRole role;
    public ImageStoreTO(ImageStoreInfo dataStore) {
        this.type = dataStore.getType();
        this.uri = dataStore.getUri();
        this.providerName = null;
        this.role = dataStore.getRole();
    }
    
    public String getType() {
        return this.type;
    }
    
    public String getUri() {
        return this.uri;
    }

    /**
     * @return the providerName
     */
    public String getProviderName() {
        return providerName;
    }

    @Override
    public DataStoreRole getRole() {
        return this.role;
    }
}
