/*
 * Kimios - Document Management System Software
 * Copyright (C) 2008-2015  DevLib'
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

package org.kimios.kernel.index.controller;

import org.kimios.exceptions.ConfigException;
import org.kimios.exceptions.AccessDeniedException;
import org.kimios.exceptions.DataSourceException;
import org.kimios.exceptions.IndexException;
import org.kimios.kernel.index.ReindexerProcess;
import org.kimios.kernel.security.model.Session;

import java.util.List;
import java.util.concurrent.TimeUnit;


public interface ISearchManagementController {


    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.ISearchManagementController#reindex(org.kimios.kernel.security.Session, java.lang.String)
    */
    public void reindex(Session session, String path)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;

    /* (non-Javadoc)
    * @see org.kimios.kernel.controller.impl.ISearchManagementController#getReindexProgress(org.kimios.kernel.security.Session)
    */
    public int getReindexProgress(Session session)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;


    public List<String> listDocumentAvailableFields(Session session)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;


    public List<ReindexerProcess.ReindexResult> viewIndexingProcess(Session session)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;

    public void parallelReindex(Session session, List<String> paths, List<Long> excludedDocuments, List<String> excludedExtensions,
                                Integer blockSize,
                                Long readFileTimeOut,
                                TimeUnit readFileTimeoutUnit,
                                int threadPoolSize,
                                boolean updateDocsMetaWrapper,
                                boolean disableThreading,
                                int entityType)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;


    public void parallelReindex(Session session, List<Long> ids,
                                             Integer blockSize,
                                             Long readFileTimeOut,
                                             TimeUnit readFileTimeoutUnit,
                                             int threadPoolSize,
                                             boolean updateDocsMetaWrapper,
                                             boolean disableThreading,
                                             int entityType)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;

    public void killAndCleanReindexProcess(Session session)
            throws AccessDeniedException, IndexException, ConfigException, DataSourceException;

}

