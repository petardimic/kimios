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
package org.kimios.kernel.index;

import org.apache.solr.client.solrj.SolrQuery;
import org.kimios.exceptions.ConfigException;
import org.kimios.kernel.dms.DMEntity;
import org.kimios.kernel.exception.DataSourceException;
import org.kimios.kernel.exception.IndexException;
import org.kimios.kernel.index.query.model.SearchResponse;

import java.util.List;

/**
 * Solr Index Manager Interface
 */
public interface ISolrIndexManager
    extends AbstractIndexManager
{
    public SearchResponse executeSolrQuery( SolrQuery query )
        throws IndexException;

    public void indexDocumentList( List<DMEntity> documentEntities )
        throws IndexException, DataSourceException, ConfigException;

    public void deleteByQuery( String query )
        throws IndexException;

    public List<String> filterFields();

}
