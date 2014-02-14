/*
 * Kimios - Document Management System Software
 * Copyright (C) 2008-2014  DevLib'
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * aong with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kimios.webservices.impl.factory;

import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.kimios.webservices.pojo.ProcessWrapper;

public class ProcessWrapperFactory {

    public static ProcessWrapper createProcessWrapper(ProcessDeploymentInfo process) {
        ProcessWrapper wrapper = new ProcessWrapper();
        wrapper.setId(process.getId());
        wrapper.setProcessId(process.getProcessId());
        wrapper.setName(process.getName());
        wrapper.setDescription(process.getDescription());
        wrapper.setActivationState(process.getActivationState() != null ? process.getActivationState().name() : null);
        wrapper.setConfigurationState(process.getConfigurationState() != null ? process.getConfigurationState().name() : null);
        wrapper.setDeployedBy(process.getDeployedBy());
        wrapper.setDeploymentDate(process.getDeploymentDate());
        wrapper.setDisplayName(process.getDisplayName());
        wrapper.setDisplayDescription(process.getDisplayDescription());
        wrapper.setIconPath(process.getIconPath());
        wrapper.setLastUpdateDate(process.getLastUpdateDate());
        wrapper.setVersion(process.getVersion());
        return wrapper;
    }

}
