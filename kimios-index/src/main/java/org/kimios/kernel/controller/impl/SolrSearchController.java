/*
 * Kimios - Document Management System Software
 * Copyright (C) 2008-2012  DevLib'
 *
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kimios.kernel.controller.impl;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.kimios.exceptions.ConfigException;
import org.kimios.kernel.controller.AKimiosController;
import org.kimios.kernel.controller.ISearchController;
import org.kimios.kernel.dms.*;
import org.kimios.kernel.dms.DMEntity;
import org.kimios.kernel.dms.Document;
import org.kimios.kernel.dms.DocumentType;
import org.kimios.kernel.dms.Folder;
import org.kimios.kernel.dms.Meta;
import org.kimios.kernel.dms.Workspace;
import org.kimios.kernel.exception.AccessDeniedException;
import org.kimios.kernel.exception.DataSourceException;
import org.kimios.kernel.exception.IndexException;
import org.kimios.kernel.index.ISolrIndexManager;
import org.kimios.kernel.index.query.FacetQueryBuilder;
import org.kimios.kernel.index.query.QueryBuilder;
import org.kimios.kernel.index.query.factory.*;
import org.kimios.kernel.index.query.factory.DocumentFactory;
import org.kimios.kernel.index.query.model.Criteria;
import org.kimios.kernel.index.query.model.DmsSolrFields;
import org.kimios.kernel.index.query.model.SearchRequest;
import org.kimios.kernel.index.query.model.SearchResponse;
import org.kimios.kernel.security.Session;
import org.kimios.kernel.ws.pojo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author Fabien Alin
 * @version 1.0
 */
@Transactional
public class SolrSearchController
    extends AKimiosController
    implements ISearchController
{
    private static Logger log = LoggerFactory.getLogger( SolrSearchController.class );

    private ISolrIndexManager solrIndexManager;

    private SearchRequestFactory searchRequestFactory;

    public SearchRequestFactory getSearchRequestFactory()
    {
        return searchRequestFactory;
    }

    public void setSearchRequestFactory( SearchRequestFactory searchRequestFactory )
    {
        this.searchRequestFactory = searchRequestFactory;
    }

    public ISolrIndexManager getSolrIndexManager()
    {
        return solrIndexManager;
    }

    public void setSolrIndexManager( ISolrIndexManager solrIndexManager )
    {
        this.solrIndexManager = solrIndexManager;
    }

    private SearchResponse quickSearchIds( Session session, String query, DMEntity entity, int start, int pageSize,
                                           String sortField, String sortDir )
    {
        String documentNameQuery = QueryBuilder.documentNameQuery( query );

        SolrQuery indexQuery = new SolrQuery();

        ArrayList<String> filterQueries = new ArrayList<String>();
        if ( !getSecurityAgent().isAdmin( session.getUserName(), session.getUserSource() ) )
        {
            String aclQuery = QueryBuilder.buildAclQuery( session );
            filterQueries.add( aclQuery );
        }
        if ( entity != null )
        {
            DMEntity entityLoaded = dmsFactoryInstantiator.getDmEntityFactory().getEntity( entity.getUid() );
            String pathQuery = "DocumentParent:" + ClientUtils.escapeQueryChars(entityLoaded.getPath() + "/") + "*";
            filterQueries.add( pathQuery );
        }
        indexQuery.setFilterQueries( filterQueries.toArray( new String[]{ } ) );
        if ( sortField != null )
        {
            indexQuery.addSort( sortField,
                              SolrQuery.ORDER.valueOf( ( sortDir != null ? sortDir.toLowerCase() : "asc" ) ) );
        }
        indexQuery.addSort( "score", SolrQuery.ORDER.desc );
        indexQuery.setQuery( documentNameQuery );
        if ( start > -1 && pageSize > -1 )
        {
            indexQuery.setStart( start );
            indexQuery.setRows( pageSize );
        }

        SearchResponse searchResponse = solrIndexManager.executeSolrQuery( indexQuery );
        return searchResponse;
    }

    public List<Document> quickSearch( Session session, String query, DMEntity entity )
        throws IndexException, DataSourceException, ConfigException
    {
        return dmsFactoryInstantiator.getDocumentFactory().getDocumentsFromIds(
            quickSearchIds( session, query, entity, -1, -1, null, null ).getDocumentIds() );
    }

    public SearchResponse quickSearchPojos( Session session, String query, DMEntity entity, int start, int pageSize,
                                            String sortField, String sortDir )
        throws IndexException, DataSourceException, ConfigException
    {
        SearchResponse searchResponse = quickSearchIds( session, query, entity, start, pageSize, sortField, sortDir );
        return searchResponse;
    }

    public SearchResponse quickSearchPojos( Session session, String query, long dmEntityUid, int start, int pageSize,
                                            String sortField, String sortDir )
            throws IndexException, DataSourceException, ConfigException
    {
        DMEntity e = dmsFactoryInstantiator.getDmEntityFactory().getEntity(dmEntityUid);
        DMEntity entity = null;
        if( e != null ){
        switch (e.getType()) {
            case DMEntityType.WORKSPACE:
                entity = new Workspace(dmEntityUid, "", "", "", null);
                break;
            case DMEntityType.FOLDER:
                entity = new Folder(dmEntityUid, "", "", "", null, -1, -1);
                break;
        }
        }
        if (entity != null) {
            return quickSearchPojos(session, query, entity, start, pageSize, sortField, sortDir);
        } else {
            return quickSearchPojos(session, query, null, start, pageSize, sortField, sortDir);
        }
    }

    @Deprecated
    public List<Document> advancedSearch( Session session, String xmlStream, DMEntity entity )
        throws DataSourceException, ConfigException, IndexException, IOException, ParserConfigurationException,
        SAXException
    {
        SearchResponse searchResponse = this.advancedSearchIds( session, xmlStream, entity, null, null );
        return dmsFactoryInstantiator.getDocumentFactory().getDocumentsFromIds( searchResponse.getDocumentIds() );
    }


    @Deprecated
    public List<org.kimios.kernel.ws.pojo.Document> advancedSearchPojos( Session session, String xmlStream,
                                                                         DMEntity entity )
            throws DataSourceException, ConfigException, IndexException, IOException, ParserConfigurationException,
            SAXException
    {
        SearchResponse searchResponse = this.advancedSearchIds( session, xmlStream, entity, null, null );
        return dmsFactoryInstantiator.getDocumentFactory().getDocumentsPojosFromIds( searchResponse.getDocumentIds() );
    }

    @Deprecated
    public List<org.kimios.kernel.ws.pojo.Document> advancedSearchPojos( Session session, String xmlStream,
                                                                         long dmEntityUid)
            throws DataSourceException, ConfigException, IndexException, IOException, ParserConfigurationException,
            SAXException
    {
        DMEntity e = dmsFactoryInstantiator.getDmEntityFactory().getEntity(dmEntityUid);
        DMEntity entity = null;
        switch (e.getType()) {
            case DMEntityType.WORKSPACE:
                entity = new Workspace(dmEntityUid, "", "", "", null);
                break;
            case DMEntityType.FOLDER:
                entity = new Folder(dmEntityUid, "", "", "", null, -1, -1);
                break;
        }

        SearchResponse searchResponse = this.advancedSearchIds( session, xmlStream, entity, null, null );
        return dmsFactoryInstantiator.getDocumentFactory().getDocumentsPojosFromIds( searchResponse.getDocumentIds() );
    }


    @Deprecated
    private SearchResponse advancedSearchIds( Session session, String xmlStream, DMEntity entity, String sortField,
                                              String sortDir )
        throws DataSourceException, ConfigException, IndexException, IOException, ParserConfigurationException,
        SAXException
    {
        SolrQuery indexQuery = new SolrQuery();

        ArrayList<String> aclFilterQueries = new ArrayList<String>();
        ArrayList<String> filterQueries = new ArrayList<String>();
        if ( !getSecurityAgent().isAdmin( session.getUserName(), session.getUserSource() ) )
        {
            String aclQuery = QueryBuilder.buildAclQuery( session );
            aclFilterQueries.add( aclQuery );
        }
        if ( entity != null )
        {
            DMEntity entityLoaded = dmsFactoryInstantiator.getDmEntityFactory().getEntity( entity.getUid() );
            String pathQuery = "DocumentParent:" + entityLoaded.getPath() + "/*";
            filterQueries.add( pathQuery );
        }
        //Parsing XML stream

        List<String> queries = new ArrayList<String>();
        org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new java.io.ByteArrayInputStream( xmlStream.getBytes( "UTF-8" ) ) );
        Element root = doc.getDocumentElement();
        NodeList childNodes = root.getChildNodes();
        for ( int i = 0; i < childNodes.getLength(); i++ )
        {
            Node node = childNodes.item( i );
            //Document name query
            if ( node.getNodeName().equals( "document-name" ) )
            {
                if ( node.getTextContent() != null && !node.getTextContent().equals( "" ) )
                {
                    queries.add( QueryBuilder.documentNameQuery( node.getTextContent().toLowerCase() ) );
                }
            }
            //Document body query
            if ( node.getNodeName().equals( "text" ) )
            {
                if ( node.getTextContent() != null && !node.getTextContent().equals( "" ) )
                {
                    String bodyQuery = ClientUtils.escapeQueryChars( node.getTextContent() );
                    queries.add( "DocumentBody:" + bodyQuery );
                }
            }
            //Document uid query
            if ( node.getNodeName().equals( "document-uid" ) )
            {
                if ( node.getTextContent() != null && !node.getTextContent().equals( "" ) )
                {
                    filterQueries.add( "DocumentUid:" + node.getTextContent() );
                }
            }
            //Document type query
            if ( node.getNodeName().equals( "document-type-uid" ) )
            {
                if ( node.getTextContent() != null && !node.getTextContent().equals( "" ) )
                {
                    long dtUid = Long.parseLong( node.getTextContent() );
                    List<DocumentType> items =
                        dmsFactoryInstantiator.getDocumentTypeFactory().getChildrenDocumentType( dtUid );
                    List<DocumentType> documentTypeList = new ArrayList<DocumentType>( items );
                    for ( DocumentType it : items )
                    {
                        documentTypeList.addAll(
                            dmsFactoryInstantiator.getDocumentTypeFactory().getChildrenDocumentType( it.getUid() ) );
                    }

                    StringBuilder builder = new StringBuilder();

                    builder.append( "DocumentTypeUid:(" + dtUid + ( documentTypeList.size() > 0 ? " OR " : "" ) );
                    int idx = 1;
                    for ( DocumentType dtIt : documentTypeList )
                    {
                        builder.append( dtIt.getUid() );
                        idx++;
                        if ( idx < documentTypeList.size() )
                        {
                            builder.append( " OR " );
                        }
                    }
                    builder.append( ")" );
                    filterQueries.add( builder.toString() );
                }
            }

            //Meta data queries
            if ( node.getNodeName().equals( "meta-value" ) )
            {
                Meta meta = dmsFactoryInstantiator.getMetaFactory().getMeta(
                    Long.parseLong( node.getAttributes().getNamedItem( "uid" ).getTextContent() ) );
                if ( meta != null )
                {
                    if ( meta.getMetaType() == MetaType.STRING )
                    {
                        if ( node.getTextContent() != null && !node.getTextContent().equals( "" ) )
                        {

                            String metaStringQuery = "MetaDataString_" + meta.getUid() + ":*" +
                                ClientUtils.escapeQueryChars( node.getTextContent() ) + "*";
                            queries.add( metaStringQuery );
                        }
                    }
                    if ( meta.getMetaType() == MetaType.NUMBER )
                    {
                        Double min = null;
                        Double max = null;
                        boolean toAdd = false;
                        if ( node.getAttributes().getNamedItem( "number-from" ) != null
                            && !node.getAttributes().getNamedItem( "number-from" ).getTextContent().equals( "" ) )
                        {
                            min = Double.parseDouble(
                                node.getAttributes().getNamedItem( "number-from" ).getTextContent() );
                            toAdd = true;
                        }
                        if ( node.getAttributes().getNamedItem( "number-to" ) != null
                            && !node.getAttributes().getNamedItem( "number-to" ).getTextContent().equals( "" ) )
                        {
                            max =
                                Double.parseDouble( node.getAttributes().getNamedItem( "number-to" ).getTextContent() );
                            toAdd = true;
                        }
                        if ( toAdd )
                        {
                            String metaNumberQuery =
                                "MetaDataNumber_" + meta.getUid() + ":[" + ( min != null ? min : "*" ) + " TO " + (
                                    max != null ? max : "*" ) + "]";

                            queries.add( metaNumberQuery );
                        }
                    }
                    if ( meta.getMetaType() == MetaType.DATE )
                    {
                        Date min = null;
                        Date max = null;
                        boolean toAdd = false;
                        if ( node.getAttributes().getNamedItem( "date-from" ) != null
                            && !node.getAttributes().getNamedItem( "date-from" ).getTextContent().equals( "" ) )
                        {

                            min = new Date(
                                Long.parseLong( node.getAttributes().getNamedItem( "date-from" ).getTextContent() ) );
                            toAdd = true;
                        }
                        if ( node.getAttributes().getNamedItem( "date-to" ) != null
                            && !node.getAttributes().getNamedItem( "date-to" ).getTextContent().equals( "" ) )
                        {

                            max = new Date(
                                Long.parseLong( node.getAttributes().getNamedItem( "date-to" ).getTextContent() ) );
                            toAdd = true;
                        }
                        if ( toAdd )
                        {
                            DateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss'Z'" );
                            df.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
                            String metaDateQuery =
                                "MetaDataDate_" + meta.getUid() + ":[" + ( min != null ? df.format( min ) : "*" )
                                    + " TO " + ( max != null ? df.format( max ) : "*" ) + "]";
                            queries.add( metaDateQuery );
                        }
                    }
                    if ( meta.getMetaType() == MetaType.BOOLEAN )
                    {
                        if ( node.getAttributes().getNamedItem( "boolean-value" ) != null
                            && !node.getAttributes().getNamedItem( "boolean-value" ).getTextContent().equals( "" ) )
                        {

                            String metaBoolQuery = "MetaDataBoolean_" + meta.getUid() + ":" + Boolean.parseBoolean(
                                node.getAttributes().getNamedItem( "boolean-value" ).getTextContent() );
                            queries.add( metaBoolQuery );
                        }
                    }
                }
            }
        }

        indexQuery.setFilterQueries( aclFilterQueries.toArray( new String[]{ } ) );
        indexQuery.addSortField( "score", SolrQuery.ORDER.desc );
        StringBuilder sQuery = new StringBuilder();
        for ( String q : queries )
        {

            sQuery.append( "+" );
            sQuery.append( q );
            sQuery.append( " " );
        }

        if ( queries.size() == 0 )
        {
            /*
                Convert filter queries in query, to get result
             */
            for ( String q : filterQueries )
            {

                sQuery.append( "+" );
                sQuery.append( q );
                sQuery.append( " " );
            }
        }

        log.debug( "Solr Final Query: " + sQuery );

        indexQuery.setQuery( sQuery.toString() );
        SearchResponse response = solrIndexManager.executeSolrQuery( indexQuery );
        return response;
    }


    public void saveSearchQuery( Session session, Long id, String name, List<Criteria> criteriaList, String sortField,
                                 String sortDir )
        throws DataSourceException, ConfigException, IndexException, IOException
    {
        SearchRequest searchRequest = new SearchRequest();
        if ( id != null )
        {
            searchRequest.setId( id ); // update mode
        }
        searchRequest.setName( name );
        searchRequest.setCriteriaList( criteriaList );
        searchRequest.setOwner( session.getUserName() );
        searchRequest.setOwnerSource( session.getUserSource() );
        searchRequest.setSortField( sortField );
        searchRequest.setSortDir( sortDir );
        searchRequestFactory.save( searchRequest );
    }

    public void deleteSearchQuery( Session session, Long id )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException
    {
        SearchRequest searchRequest = searchRequestFactory.loadById( id );
        if ( searchRequest == null || !( searchRequest.getOwner().equals( session.getUserName() )
            && searchRequest.getOwnerSource().equals( session.getUserSource() ) ) )
        {
            throw new AccessDeniedException();
        }
        searchRequestFactory.deleteSearchRequest( id );
    }

    public SearchRequest loadSearchQuery( Session session, Long id )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException
    {
        SearchRequest searchRequest = searchRequestFactory.loadById( id );
        if ( searchRequest == null || !( searchRequest.getOwner().equals( session.getUserName() )
            && searchRequest.getOwnerSource().equals( session.getUserSource() ) ) )
        {
            throw new AccessDeniedException();
        }
        return searchRequest;
    }

    public List<SearchRequest> listSavedSearch( Session session )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException
    {
        return searchRequestFactory.loadSearchRequest( session.getUserName(), session.getUserSource() );
    }


    public void updateSearchQuery( Session session, Long id, String name, List<Criteria> criteriaList, int start,
                                   int pageSize, String sortField, String sortDir )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException
    {
        SearchRequest searchRequest = searchRequestFactory.loadById( id );
        if ( searchRequest == null || !( searchRequest.getOwner().equals( session.getUserName() )
            && searchRequest.getOwnerSource().equals( session.getUserSource() ) ) )
        {
            throw new AccessDeniedException();
        }

        searchRequest.setName( name );
        searchRequest.setCriteriaList( criteriaList );
        searchRequest.setOwner( session.getUserName() );
        searchRequest.setOwnerSource( session.getUserSource() );
        searchRequestFactory.save( searchRequest );
    }

    public List<SearchRequest> searchRequestList( Session session )
    {
        return searchRequestFactory.loadSearchRequest( session.getUserName(), session.getUid() );
    }


    public SearchResponse advancedSearchDocuments( Session session, List<Criteria> criteriaList, int start,
                                                   int pageSize, String sortField, String sortDir, String virtualPath )
        throws DataSourceException, ConfigException, IndexException, IOException, ParseException
    {

        SolrQuery query =
            this.parseQueryFromListCriteria( session, start, pageSize, criteriaList, sortField, sortDir, virtualPath );
        SearchResponse searchResponse = solrIndexManager.executeSolrQuery( query );
        return searchResponse;

    }


    public SearchResponse advancedSearchDocuments( Session session, List<Criteria> criteriaList, int start,
                                                   int pageSize, String sortField, String sortDir )
        throws DataSourceException, ConfigException, IndexException, IOException, ParseException
    {

        return advancedSearchDocuments( session, criteriaList, start, pageSize, sortField, sortDir, null );
    }

    private SolrQuery parseQueryFromListCriteria( Session session, int page, int pageSize, List<Criteria> criteriaList,
                                                  String sortField, String sortDir )
        throws ParseException
    {

        return this.parseQueryFromListCriteria( session, page, pageSize, criteriaList, sortField, sortDir, null );
    }

    private SolrQuery parseQueryFromListCriteria( Session session, int page, int pageSize, List<Criteria> criteriaList,
                                                  String sortField, String sortDir, String virtualPath )
        throws ParseException
    {

        SolrQuery indexQuery = new SolrQuery();

        SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd" );
        sdf.setTimeZone( TimeZone.getTimeZone( "UTC" ) );

        ArrayList<String> aclFilterQueries = new ArrayList<String>();
        ArrayList<String> filterQueries = new ArrayList<String>();
        ArrayList<String> queries = new ArrayList<String>();
        if ( !getSecurityAgent().isAdmin( session.getUserName(), session.getUserSource() ) )
        {
            String aclQuery = QueryBuilder.buildAclQuery( session );
            aclFilterQueries.add( aclQuery );
        }

        boolean someFacet = false;

        int requiredDepth = -1;
        Map<Integer, String> pathIndex = new HashMap<Integer, String>();
        if ( virtualPath != null )
        {
            if(virtualPath.startsWith( "/" ))
                virtualPath = virtualPath.substring( 1 );
            String[] vPaths = virtualPath.split( "/" );
            for ( String p : vPaths )
            {
                requiredDepth++;
                pathIndex.put( requiredDepth, p );
            }
        }


        for ( Criteria c : criteriaList )
        {

            if ( c.isFaceted() )
            {


                log.info(" > Required level " + requiredDepth + ". Path Index Size: " + pathIndex.size() + " " + virtualPath + "  / c: " + c.getLevel() + " => " + c.getFieldName());
                if ( c.getLevel() != null && c.getLevel() > -1 && pathIndex.containsKey( c.getLevel() ) )
                {
                    /*
                        Gen filter queries
                     */
                    Class fieldType = DmsSolrFields.sortFieldMapping.get( c.getFieldName() );
                    if ( fieldType.equals( Date.class ) )
                    {
                        String pathDate = pathIndex.get( c.getLevel() );
                        String dateQuery = c.getFieldName() + ":[" + pathDate + "]";
                        filterQueries.add( dateQuery );
                    }
                    else if ( Number.class.isAssignableFrom( fieldType ) )
                    {
                        String numberPath = pathIndex.get( c.getLevel() );
                        String numberQuery = c.getFieldName() + ":[" + numberPath +"]";
                        filterQueries.add( numberQuery );
                    }
                    else if ( fieldType.equals( String.class ) )
                    {
                        String stringQuery = c.getFieldName() + ":" + ClientUtils.escapeQueryChars( pathIndex.get( c.getLevel() ));
                        filterQueries.add( stringQuery );
                    }
                }
                else
                {
                    try
                    {
                        /*
                            Apply facet if at required level
                         */
                        if(c.getLevel().equals(pathIndex.size())){
                            log.info("Using criteria for facet: " + c.getFieldName() + " / " + c.getFacetField());
                            Class fieldType = DmsSolrFields.sortFieldMapping.get( c.getFieldName() );
                            if ( fieldType.equals( Date.class ) )
                            {
                                indexQuery =
                                        FacetQueryBuilder.dateFacetBuiler( indexQuery, c.getFieldName(), c.getRangeMin(),
                                                c.getRangeMax(), c.getDateFacetGapType(),
                                                c.getDateFacetGapRange() );
                            }
                            else if ( Number.class.isAssignableFrom( fieldType ) )
                            {
                                indexQuery =
                                        FacetQueryBuilder.numberFacetBuiler( indexQuery, c.getFieldName(), c.getRangeMin(),
                                                c.getRangeMax(), c.getFacetRangeGap() );
                            }
                            else if ( fieldType.equals( String.class ) )
                            {
                                indexQuery =
                                        FacetQueryBuilder.stringFacetBuilder( indexQuery, c.getFieldName(), c.getQuery() );
                            }
                            else
                            {
                                throw new IndexException( "UnknownFacetFieldType" );
                            }

                            someFacet = c.isFaceted();

                        } else {
                            log.info("Ignoring criteria: " + c.getFieldName() + " / " + c.getFacetField());
                        }
                    }
                    catch ( Exception e )
                    {
                        e.printStackTrace();
                    }
                }
            }
            else if ( c.getFiltersValues() != null && c.getFiltersValues().size() > 0 )
            {
                for ( String e : c.getFiltersValues() )
                {
                    filterQueries.add( c.getFieldName() + ":" + e );
                }
            }
            else if ( c.getQuery() != null && c.getQuery().trim().length() > 0 || c.getRangeMin() != null
                || c.getRangeMax() != null )
            {
                if ( c.getFieldName().equals( "DocumentName" ) )
                {
                    queries.add( QueryBuilder.documentNameQuery( c.getQuery() ) );
                }
                else if ( c.getFieldName().equals( "DocumentBody" ) )
                {
                    queries.add( "DocumentBody:" + ClientUtils.escapeQueryChars( c.getQuery() ) );
                }
                else if ( c.getFieldName().equals( "DocumentUid" ) )
                {
                    filterQueries.add( "DocumentUid:" + c.getQuery() );
                }
                else if ( c.getFieldName().equals( "DocumentParent" ) )
                {
                    filterQueries.add( QueryBuilder.documentParentQuery( c.getQuery() ) );
                }
                else if ( c.getFieldName().equals( "DocumentVersionUpdateDate" ) )
                {
                    queries.add(
                        QueryBuilder.dateQuery( "DocumentVersionUpdateDate", c.getRangeMin(), c.getRangeMax() ) );
                }
                else if ( c.getFieldName().equals( "DocumentCreationDate" ) )
                {
                    queries.add( QueryBuilder.dateQuery( "DocumentCreationDate", c.getRangeMin(), c.getRangeMax() ) );
                }
                else if ( c.getFieldName().equals( "DocumentVersionHash" ) )
                {
                    queries.add( "DocumentVersionHash:" + ClientUtils.escapeQueryChars(c.getQuery()) );
                }
                else if ( c.getFieldName().equals( "DocumentTypeUid" ) )
                {
                    long dtUid = Long.parseLong( c.getQuery() );
                    List<DocumentType> items =
                        dmsFactoryInstantiator.getDocumentTypeFactory().getChildrenDocumentType( dtUid );
                    List<DocumentType> documentTypeList = new ArrayList<DocumentType>( items );
                    for ( DocumentType it : items )
                    {
                        documentTypeList.addAll(
                            dmsFactoryInstantiator.getDocumentTypeFactory().getChildrenDocumentType( it.getUid() ) );
                    }

                    StringBuilder builder = new StringBuilder();

                    builder.append( "DocumentTypeUid:(" + dtUid + ( documentTypeList.size() > 0 ? " OR " : "" ) );
                    int idx = 0;
                    for ( DocumentType dtIt : documentTypeList )
                    {
                        builder.append( dtIt.getUid() );
                        idx++;
                        if ( idx < documentTypeList.size() )
                        {
                            builder.append( " OR " );
                        }
                    }
                    builder.append( ")" );
                    filterQueries.add( builder.toString() );
                }
                else if ( c.getFieldName().startsWith( "MetaData" ) )
                {
                    Meta meta = dmsFactoryInstantiator.getMetaFactory().getMeta( c.getMetaId() );
                    if ( meta != null )
                    {
                        if ( meta.getMetaType() == MetaType.STRING )
                        {

                            String metaStringQuery = "MetaDataString_" + meta.getUid() + ":*" +
                                ClientUtils.escapeQueryChars( c.getQuery().toLowerCase() ) + "*";
                            queries.add( metaStringQuery );
                        }
                        if ( meta.getMetaType() == MetaType.NUMBER )
                        {
                            Double min = null;
                            Double max = null;
                            boolean toAdd = false;
                            if ( c.getRangeMin() != null && c.getRangeMin().trim().length() > 0 )
                            {
                                min = Double.parseDouble( c.getRangeMin() );
                                toAdd = true;
                            }
                            if ( c.getRangeMax() != null && c.getRangeMax().trim().length() > 0 )
                            {
                                max = Double.parseDouble( c.getRangeMax() );
                                toAdd = true;
                            }
                            if ( toAdd )
                            {
                                String metaNumberQuery =
                                    QueryBuilder.numberQuery( "MetaDataNumber_" + meta.getUid(), c.getRangeMin(),
                                                              c.getRangeMax() );

                                queries.add( metaNumberQuery );
                            }
                        }
                        if ( meta.getMetaType() == MetaType.DATE )
                        {
                            Date min = null;
                            Date max = null;
                            boolean toAdd = false;
                            if ( c.getRangeMin() != null && c.getRangeMin().trim().length() > 0 )
                            {

                                try
                                {
                                    min = sdf.parse( c.getRangeMin() );
                                    toAdd = true;
                                }
                                catch ( Exception e )
                                {
                                    toAdd = false;
                                }
                            }
                            if ( c.getRangeMax() != null && c.getRangeMax().trim().length() > 0 )
                            {
                                try
                                {
                                    max = sdf.parse( c.getRangeMax() );
                                    toAdd = true;
                                }
                                catch ( Exception e )
                                {
                                    toAdd = false;
                                }
                            }
                            if ( toAdd )
                            {
                                DateFormat df = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss'Z'" );
                                df.setTimeZone( TimeZone.getTimeZone( "UTC" ) );
                                String metaDateQuery =
                                    QueryBuilder.dateQuery( "MetaDataDate_" + meta.getUid(), c.getRangeMin(),
                                                            c.getRangeMax() );

                                queries.add( metaDateQuery );
                            }
                        }
                        if ( meta.getMetaType() == MetaType.BOOLEAN )
                        {
                            String metaBoolQuery =
                                "MetaDataBoolean_" + meta.getUid() + ":" + Boolean.parseBoolean( c.getQuery() );
                            queries.add( metaBoolQuery );
                        }
                    }
                }
            }
        }

        indexQuery.setFilterQueries( filterQueries.toArray( new String[]{ } ) );
        if ( sortField != null )
        {
            indexQuery.addSortField( sortField, sortDir != null
                ? SolrQuery.ORDER.valueOf( sortDir.toLowerCase() )
                : SolrQuery.ORDER.asc );
        }
        indexQuery.addSortField( "score", SolrQuery.ORDER.desc );
        StringBuilder sQuery = new StringBuilder();

        for ( String q : queries )
        {
            sQuery.append( "+" );
            sQuery.append( q );
            sQuery.append( " " );
        }

        if ( queries.size() == 0 )
        {
            /*
                Convert filter queries in query, to get result
             */

            if ( someFacet )
            {
                sQuery.append( "*:*" );
            }
            else
            {
                for ( String q : filterQueries )
                {
                    sQuery.append( "+" );
                    sQuery.append( q );
                    sQuery.append( " " );
                }
            }

        }
        else
        {
            filterQueries.addAll( aclFilterQueries );
            indexQuery.setFilterQueries( filterQueries.toArray( new String[]{ } ) );

        }
        log.debug( "Solr Final Query: " + sQuery );
        indexQuery.setQuery( sQuery.toString() );
        if ( pageSize > -1 && page > -1 )
        {
            indexQuery.setRows( pageSize );
            indexQuery.setStart( page );
        }
        else
        {
            indexQuery.setRows( Integer.MAX_VALUE );
        }

        return indexQuery;

    }


    public SearchResponse executeSearchQuery( Session session, Long id, int start, int pageSize, String sortField,
                                              String sortDir )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException, ParseException
    {
        return this.executeSearchQueryOrBrowse( session, id, start, pageSize, sortField, sortDir, null );
    }


    public SearchResponse executeSearchQueryOrBrowse( Session session, Long id, int start, int pageSize,
                                                      String sortField, String sortDir, String virtualPath )
        throws AccessDeniedException, DataSourceException, ConfigException, IndexException, IOException, ParseException
    {
        SearchRequest searchRequest = searchRequestFactory.loadById( id );
        log.info(" >> " + searchRequest.toString());
        if ( searchRequest == null || (!( searchRequest.getOwner().equals( session.getUserName() )
            && searchRequest.getOwnerSource().equals( session.getUserSource() )) && !searchRequest.getPublicAccess()))
        {
            throw new AccessDeniedException();
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<Criteria> criteriaList =
            objectMapper.readValue( searchRequest.getCriteriasListJson(), new TypeReference<List<Criteria>>()
            {
            } );
        SolrQuery query =
            this.parseQueryFromListCriteria( session, start, pageSize, criteriaList, sortField, sortDir, virtualPath );
        SearchResponse searchResponse = this.solrIndexManager.executeSolrQuery( query );
        return searchResponse;
    }


    @Override
    public List<String> listAvailableFields(Session session) throws AccessDeniedException, IndexException {
        return solrIndexManager.filterFields();
    }
}