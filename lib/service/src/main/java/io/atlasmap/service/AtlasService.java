/**
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atlasmap.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.atlasmap.api.AtlasContext;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.core.AtlasUtil;
import io.atlasmap.core.DefaultAtlasContextFactory;
import io.atlasmap.core.DefaultAtlasFieldActionService;
import io.atlasmap.service.AtlasLibraryLoader.AtlasLibraryLoaderListener;
import io.atlasmap.v2.ActionDetails;
import io.atlasmap.v2.AtlasMapping;
import io.atlasmap.v2.Audits;
import io.atlasmap.v2.Json;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.ProcessMappingRequest;
import io.atlasmap.v2.ProcessMappingResponse;
import io.atlasmap.v2.StringMap;
import io.atlasmap.v2.StringMapEntry;
import io.atlasmap.v2.Validations;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api
@Path("/")
public class AtlasService {

    static final String ATLASMAP_ADM_PATH = "atlasmap.adm.path";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasService.class);

    private final DefaultAtlasContextFactory atlasContextFactory = DefaultAtlasContextFactory.getInstance();
    private final AtlasContext defaultContext;

    private String atlasmapCatalogName = "atlasmap-catalog.adm";
    private String atlasmapCatalogFilesName = "adm-catalog-files.gz";
    private String atlasmapGenericMappingsName = "atlasmapping.xml";
    private String baseFolder = "target";
    private String mappingFolder = baseFolder + File.separator + "mappings";
    private String libFolder = baseFolder + File.separator + "lib";
    private AtlasLibraryLoader libraryLoader;

    public AtlasService() throws AtlasException {
        this.defaultContext = createDefaultContext();
        this.libraryLoader = new AtlasLibraryLoader(libFolder);
        // Add atlas-core in case it runs on modular class loader
        this.libraryLoader.addAlternativeLoader(DefaultAtlasFieldActionService.class.getClassLoader());
        this.libraryLoader.addListener(new AtlasLibraryLoaderListener() {
            @Override
            public void onUpdate(AtlasLibraryLoader loader) {
                ((DefaultAtlasFieldActionService)atlasContextFactory.getFieldActionService()).init(libraryLoader);
            }
        });
    }

    private AtlasContext createDefaultContext() throws AtlasException {
        String atlasmapAdmPath = System.getProperty(ATLASMAP_ADM_PATH);
        if (atlasmapAdmPath != null) {
            URI atlasMappingUri = new File(atlasmapAdmPath).toURI();
            return atlasContextFactory.createContext(atlasMappingUri);
        } else {
            return atlasContextFactory.createContext(new AtlasMapping());
        }
    }

    @GET
    @Path("/fieldActions")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List FieldActions", notes = "Retrieves a list of available field action")
    @ApiResponses(@ApiResponse(code = 200, response = ActionDetails.class, message = "Return a list of field action detail"))
    public Response listFieldActions(@Context UriInfo uriInfo) {
        ActionDetails details = new ActionDetails();

        if (atlasContextFactory == null || atlasContextFactory.getFieldActionService() == null) {
            return Response.ok().entity(toJson(details)).build();
        }

        details.getActionDetail().addAll(atlasContextFactory.getFieldActionService().listActionDetails());
        return Response.ok().entity(toJson(details)).build();
    }

    @GET
    @Path("/mappings")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List Mappings", notes = "Retrieves a list of mapping file name saved on the server")
    @ApiResponses(@ApiResponse(code = 200, response = StringMap.class, message = "Return a list of a pair of mapping file name and content"))
    public Response listMappings(@Context UriInfo uriInfo, @QueryParam("filter") final String filter) {
        StringMap sMap = new StringMap();
        LOG.debug("listMappings with filter '{}'", filter);
        java.nio.file.Path mappingFolderPath = Paths.get(mappingFolder);
        File[] mappings = mappingFolderPath.toFile().listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                if (filter != null && name != null && !name.toLowerCase().contains(filter.toLowerCase())) {
                    return false;
                }
                return (name != null ? name.matches("atlasmapping-[a-zA-Z0-9\\.\\-]+.xml") : false);
            }
        });

        if (mappings == null) {
            return Response.ok().entity(toJson(sMap)).build();
        }

        try {
            for (File mapping : mappings) {
                AtlasMapping map = getMappingFromFile(mapping.getAbsolutePath());
                StringMapEntry mapEntry = new StringMapEntry();
                mapEntry.setName(map.getName());
                UriBuilder builder = uriInfo.getBaseUriBuilder().path("v2").path("atlas").path("mapping")
                        .path(map.getName());
                mapEntry.setValue(builder.build().toString());
                sMap.getStringMapEntry().add(mapEntry);
            }
        } catch (JAXBException e) {
            throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
        }

        return Response.ok().entity(toJson(sMap)).build();
    }

    @DELETE
    @Path("/mapping/{mappingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Remove Mapping", notes = "Remove a mapping file saved on the server")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Specified mapping file was removed successfully"),
        @ApiResponse(code = 204, message = "Mapping file was not found")})
    public Response removeMappingRequest(@ApiParam("Mapping ID") @PathParam("mappingId") String mappingId) {

        java.nio.file.Path mappingFilePath = Paths
                .get(mappingFolder + File.separator + generateMappingFileName(mappingId));
        File mappingFile = mappingFilePath.toFile();

        if (mappingFile == null || !mappingFile.exists()) {
            return Response.noContent().build();
        }

        if (!mappingFile.delete()) {
            String msg = "Unable to delete mapping file " + mappingFile.toString();
            LOG.error(msg);
            throw new WebApplicationException(msg, Status.INTERNAL_SERVER_ERROR);
        }

        return Response.ok().build();
    }

    @DELETE
    @Path("/mapping/RESET")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Remove All Mappings", notes = "Remove all mapping files saved on the server")
    @ApiResponses({
        @ApiResponse(code = 200, message = "All mapping files were removed successfully"),
        @ApiResponse(code = 204, message = "Unable to remove all mapping files")})
    public Response resetMappings() {
        LOG.debug("resetMappings");

        java.nio.file.Path mappingFolderPath = Paths.get(mappingFolder);
        File[] mappings = mappingFolderPath.toFile().listFiles();

        if (mappings == null) {
            return Response.ok().build();
        }

        try {
            for (File mappingFile : mappings) {
                if (mappingFile.exists()) {
                    if (!mappingFile.delete()) {
                        LOG.warn("Unable to delete mapping file " + mappingFile.toString());
                    }
                }
            }
            java.nio.file.Path libFolderPath = Paths.get(libFolder);
            AtlasUtil.deleteDirectoryContents(libFolderPath.toFile());
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/mapping/{mappingFormat}/{mappingId}")
    @Produces({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML,MediaType.APPLICATION_OCTET_STREAM})
    @ApiOperation(value = "Get Mapping", notes = "Retrieve a mapping file saved on the server")
    @ApiResponses({
        @ApiResponse(code = 200, response = AtlasMapping.class, message = "Return a mapping file content"),
        @ApiResponse(code = 204, message = "Mapping file was not found"),
        @ApiResponse(code = 500, message = "Mapping file access error")})
    public Response getMappingRequest(
      @ApiParam("Mapping Format") @PathParam("mappingFormat") String mappingFormat,
      @ApiParam("Mapping ID") @PathParam("mappingId") String mappingId) {
        LOG.debug("getMappingRequest: {} '{}'", mappingFormat, mappingId);
        File mappingFile = getMappingFile(mappingFormat, mappingId);

        if (mappingFile == null) {
            LOG.debug("getMappingRequest: {} '{}' not found", mappingFormat, mappingId);
            return Response.noContent().build();
        }
        String mappingFilePath = mappingFile.getAbsolutePath();
        LOG.debug("getMappingRequest: {} '{}'", mappingFormat, mappingFilePath);

        switch (mappingFormat) {
        case "XML":
            String data = null;
            try {
                data = new String(Files.readAllBytes(mappingFile.toPath()));
            } catch (Exception e) {
                LOG.error("Error retrieving XML mapping " + mappingFilePath, e);
                throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
            }
            return Response.ok().entity(data).build();
        case "JSON":
            AtlasMapping atlasMapping = null;
            try {
                atlasMapping = getMappingFromFile(mappingFilePath);
            } catch (Exception e) {
                LOG.error("Error retrieving JSON mapping " + mappingFilePath, e);
                throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
            }
            return Response.ok().entity(toJson(atlasMapping)).build();
        case "GZ":
        case "ZIP":
            byte[] binData = null;
            try {
                FileInputStream inputStream = new FileInputStream(mappingFilePath);
                int length = inputStream.available();
                binData = new byte[length];
                inputStream.read(binData);
                inputStream.close();
            } catch (Exception e) {
                LOG.error("Error getting compressed mapping documents.\n" + e.getMessage(), e);
                throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
            }
            return Response.ok().entity(binData).build();
        default:
            throw new WebApplicationException("Unrecognized mapping format: " + mappingFormat, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/mapping/{mappingFormat}/{mappingId}")
    @Consumes({MediaType.APPLICATION_JSON,MediaType.APPLICATION_XML,MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create Mapping", notes = "Save a mapping file on the server")
    @ApiImplicitParams(@ApiImplicitParam(
            name = "mapping", value = "Mapping file content", dataType = "io.atlasmap.v2.AtlasMapping"))
    @ApiResponses({
        @ApiResponse(code = 200, message = "Succeeded"),
        @ApiResponse(code = 500, message = "Mapping file save error")})
    public Response createMappingRequest(InputStream mapping,
      @ApiParam("Mapping Format") @PathParam("mappingFormat") String mappingFormat,
      @ApiParam("Mapping ID") @PathParam("mappingId") String mappingId,
      @Context UriInfo uriInfo) {
        LOG.debug("createMappingRequest (save) with format '{}'", mappingFormat);
        UriBuilder builder = uriInfo.getAbsolutePathBuilder();
        File mappingDir = new File(mappingFolder);
        if (!mappingDir.exists()) {
            mappingDir.mkdirs();
        }

        switch (mappingFormat) {
        case "XML":
           return saveMapping(fromXml(mapping, AtlasMapping.class), uriInfo);
        case "JSON":
           return saveMapping(fromJson(mapping, AtlasMapping.class), uriInfo);
        case "GZ":
            LOG.debug("  saveCompressedMappingRequest '{}' - ID: {}", atlasmapCatalogFilesName, mappingId);
            try {
                createMappingFile(atlasmapCatalogFilesName, mapping);
                createCompressedCatalog(mappingId);
            } catch (Exception e) {
                LOG.error("Error saving compressed mapping documents.\n" + e.getMessage(), e);
                throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
            }
            builder.path(atlasmapCatalogFilesName);
            return Response.ok().location(builder.build()).build();
        case "ZIP":
            LOG.debug("  saveCompressedADMRequest '{}'", atlasmapCatalogName);
            try {
                createMappingFile(atlasmapCatalogName, mapping);
                extractCompressedCatalog();
            } catch (Exception e) {
                LOG.error("Error saving compressed mapping documents.\n" + e.getMessage(), e);
                throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
            }
            builder.path(atlasmapCatalogName);
            return Response.ok().location(builder.build()).build();
        default:
            throw new WebApplicationException("Unrecognized mapping format: " + mappingFormat, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/mapping/{mappingId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update Mapping", notes = "Update existing mapping file on the server")
    @ApiImplicitParams(@ApiImplicitParam(
            name = "mapping", value = "Mapping file content", dataType = "io.atlasmap.v2.AtlasMapping"))
    @ApiResponses(@ApiResponse(code = 200, message = "Succeeded"))
    public Response updateMappingRequest(
            InputStream mapping,
            @ApiParam("Mapping ID") @PathParam("mappingId") String mappingId,
            @Context UriInfo uriInfo) {
        return saveMapping(fromJson(mapping, AtlasMapping.class), uriInfo);
    }

    @PUT
    @Path("/mapping/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Validate Mapping", notes = "Validate mapping file")
    @ApiImplicitParams(@ApiImplicitParam(
            name = "mapping", value = "Mapping file content", dataType = "io.atlasmap.v2.AtlasMapping"))
    @ApiResponses(@ApiResponse(code = 200, response = Validations.class, message = "Return a validation result"))
    public Response validateMappingRequest(InputStream mapping, @Context UriInfo uriInfo) {
        try {
            return validateMapping(fromJson(mapping, AtlasMapping.class), uriInfo);
        } catch (AtlasException | IOException e) {
            throw new WebApplicationException(e.getMessage(), e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/mapping/process")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Process Mapping", notes = "Process Mapping by feeding input data")
    @ApiImplicitParams(@ApiImplicitParam(
            name = "request", value = "ProcessMappingRequest object", dataType = "io.atlasmap.v2.ProcessMappingRequest"))
    @ApiResponses({
        @ApiResponse(code = 200, response = ProcessMappingResponse.class, message = "Return a mapping result"),
        @ApiResponse(code = 204, message = "Skipped empty mapping execution") })
    public Response processMappingRequest(InputStream request, @Context UriInfo uriInfo) {
        ProcessMappingRequest pmr = fromJson(request, ProcessMappingRequest.class);
        if (pmr.getAtlasMapping() != null) {
            throw new WebApplicationException("Whole mapping execution is not yet supported");
        }
        Mapping mapping = pmr.getMapping();
        if (mapping == null) {
            return Response.noContent().build();
        }
        Audits audits = null;
        try {
            audits = defaultContext.processPreview(mapping);
        } catch (AtlasException e) {
            throw new WebApplicationException("Unable to process mapping preview", e);
        }
        ProcessMappingResponse response = new ProcessMappingResponse();
        response.setMapping(mapping);
        if (audits != null) {
            response.setAudits(audits);
        }
        return Response.ok().entity(toJson(response)).build();
    }

    @GET
    @Path("/ping")
    @ApiOperation(value = "Ping", notes = "Simple liveness check method used in liveness checks. Must not be protected via authetication.")
    @ApiResponses(@ApiResponse(code = 200, response = String.class, message = "Return 'pong'"))
    public String ping() {
        return "pong";
    }

    @PUT
    @Path("/library")
    @ApiOperation(value = "Upload Library", notes = "Upload a Java library archive file")
    @Consumes({MediaType.APPLICATION_OCTET_STREAM})
    @ApiResponses(@ApiResponse(
            code = 200, message = "Library upload successful."))
    public Response uploadLibrary(InputStream requestIn) {
        if (requestIn == null) {
            throw new WebApplicationException("No library file found in request body");
        }

        try {
            libraryLoader.addJarFromStream(requestIn);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("", e);
            }
            throw new WebApplicationException("Could not read file part: " + e.getMessage());
        }
        return Response.ok().build();
    }

    public AtlasLibraryLoader getLibraryLoader() {
        return this.libraryLoader;
    }

    protected Response validateMapping(AtlasMapping mapping, UriInfo uriInfo) throws IOException, AtlasException {

        File temporaryMappingFile = File.createTempFile("atlas-mapping", "xml");
        temporaryMappingFile.deleteOnExit();
        atlasContextFactory.getMappingService().saveMappingAsFile(mapping, temporaryMappingFile);

        AtlasContext context = atlasContextFactory.createContext(temporaryMappingFile.toURI());
        AtlasSession session = context.createSession();
        context.processValidation(session);
        Validations validations = session.getValidations();

        if (session.getValidations() == null) {
            validations = new Validations();
        }

        if (temporaryMappingFile.exists() && !temporaryMappingFile.delete()) {
            LOG.warn("Failed to deleting temporary file: "
                    + (temporaryMappingFile != null ? temporaryMappingFile.toString() : null));
        }

        return Response.ok().entity(toJson(validations)).build();
    }

    protected Response saveMapping(AtlasMapping mapping, UriInfo uriInfo) {
        try {
            saveMappingToFile(mapping);
        } catch (Exception e) {
            String msg = "Error saving mapping " + mapping.getName() + " to file: " + e.getMessage();
            LOG.error(msg, e);
            throw new WebApplicationException(msg, e, Status.INTERNAL_SERVER_ERROR);
        }

        UriBuilder builder = uriInfo.getAbsolutePathBuilder();
        builder.path(mapping.getName());

        return Response.ok().location(builder.build()).build();
    }

    public AtlasMapping getMappingFromFile(String fileName) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext
                .newInstance("io.atlasmap.v2:io.atlasmap.java.v2:io.atlasmap.xml.v2:io.atlasmap.json.v2");
        Marshaller marshaller = null;
        Unmarshaller unmarshaller = null;

        marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        unmarshaller = jaxbContext.createUnmarshaller();

        StreamSource fileSource = new StreamSource(new File(fileName));
        JAXBElement<AtlasMapping> mappingElem = unmarshaller.unmarshal(fileSource, AtlasMapping.class);
        if (mappingElem != null) {
            return mappingElem.getValue();
        }
        return null;
    }

    public AtlasMapping getMappingFromStream(InputStream streamSource) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext
                .newInstance("io.atlasmap.v2:io.atlasmap.java.v2:io.atlasmap.xml.v2:io.atlasmap.json.v2");
        Marshaller marshaller = null;
        Unmarshaller unmarshaller = null;

        marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        unmarshaller = jaxbContext.createUnmarshaller();

        JAXBElement<AtlasMapping> mappingElem = (JAXBElement<AtlasMapping>) unmarshaller.unmarshal(streamSource);
        if (mappingElem != null) {
            return mappingElem.getValue();
        }
        return null;
    }

    protected void saveMappingToFile(AtlasMapping atlasMapping) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext
                .newInstance("io.atlasmap.v2:io.atlasmap.java.v2:io.atlasmap.xml.v2:io.atlasmap.json.v2");
        Marshaller marshaller = null;

        marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        marshaller.marshal(atlasMapping, createMappingFile(atlasMapping.getName()));
    }

    private File createMappingFile(String mappingName) {
        File dir = new File(mappingFolder);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = mappingFolder + File.separator + generateMappingFileName(mappingName);
        LOG.debug("Creating mapping file '{}'", fileName);
        return new File(fileName);
    }

    /**
     * Write to the specified ZIP output stream the specified local file.
     *
     * @param zipOut
     * @param fileToZip
     *
     * @throws IOException
     */
    private void addFileAsZip(ZipOutputStream zipOut, String fileToZip) throws IOException {
        byte[] buffer = new byte[2048];
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileToZip));
        int count = -1;
        while ((count = in.read(buffer)) != -1) {
            zipOut.write(buffer, 0, count);
        }
        in.close();
    }

    /**
     * Create a compressed (ZIP) ADM catalog.  The contents are:
     *
     *   - the user-specified instance/schema files (JSON/XML) and mappings (XML) captured in a JSON
     *     document, compressed (GZIP).
     *   - the atlasmapping.xml file (separate for camel runtime support)
     *   - the user-specified Java archives (JAR).
     *
     * @param mappingId
     * @throws IOException
     */
    private void createCompressedCatalog(String mappingId) throws IOException {
        String compressedCatalogName = mappingFolder + File.separator + atlasmapCatalogName;
        String compressedCatalogFilesName = mappingFolder + File.separator + atlasmapCatalogFilesName;

        try {

           // AtlasMap mapping XML + Binary, compressed instance/schema/mappings + java libraries ->
           //   target/mappings/atlasmap-catalog.adm
           LOG.debug("Creating compressed catalog ADM file '{}'", compressedCatalogName);
           ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(compressedCatalogName));

           // atlasmapping.xml
           LOG.debug("  Creating compressed catalog files '{}'", atlasmapGenericMappingsName);
           ZipEntry catEntry = new ZipEntry(atlasmapGenericMappingsName);
           zipOut.putNextEntry(catEntry);
           File mappingFile = getMappingFile("XML", mappingId);

           if (mappingFile != null) {
               String mappingFilePath = mappingFile.getAbsolutePath();
               addFileAsZip(zipOut, mappingFilePath);
               zipOut.closeEntry();
           }

           // adm-catalog-files.gz
           LOG.debug("  Creating compressed catalog files '{}'", compressedCatalogFilesName);
           catEntry = new ZipEntry(atlasmapCatalogFilesName);
           zipOut.putNextEntry(catEntry);
           addFileAsZip(zipOut, compressedCatalogFilesName);
           zipOut.closeEntry();

           zipOut.putNextEntry(new ZipEntry("lib/"));
           zipOut.closeEntry();

           // .../target/lib
           java.nio.file.Path libFolderPath = Paths.get(libFolder);

           // User class libraries.
           File[] jars = libFolderPath.toFile().listFiles(new FilenameFilter() {
               @Override
               public boolean accept(File dir, String libName) {
                   if (libName != null) {
                       return true;
                   }
                   return false;
                }
           });

           try {
               for (File jarFile : jars) {
                   LOG.debug("  Creating zip file entry '{}'", "lib/" + jarFile.getName());
                   ZipEntry libEntry = new ZipEntry("lib/" + jarFile.getName());
                   zipOut.putNextEntry(libEntry);
                   addFileAsZip(zipOut, libFolderPath.toString() + File.separator  + jarFile.getName());
                   zipOut.closeEntry();
               }
           } catch (IOException e) {
               throw new IOException(e.getMessage());
           }

           zipOut.close();
       } catch (FileNotFoundException e) {
           throw new WebApplicationException("Error creating ADM catalog. " + e.getMessage(), e);
       }
    }

    /**
     * Unzip the ADM catalog file into its constituent parts:
     *
     * - target/mappings/adm-catalog-files.gz
     * - target/lib/...jar
     *
     * @throws IOException
     */
    private void extractCompressedCatalog() throws IOException {
        byte[] buffer = new byte[2048];
        String catalogName = mappingFolder + File.separator + atlasmapCatalogName;
        String catEntryname;

        try {
            ZipInputStream zipIn = new ZipInputStream(new FileInputStream(catalogName));
            ZipEntry catEntry;
            BufferedOutputStream out;

            while ((catEntry = zipIn.getNextEntry()) != null) {
                catEntryname = catEntry.getName();
                if (catEntryname.contains(atlasmapCatalogFilesName)) {
                    out = new BufferedOutputStream(new FileOutputStream(mappingFolder + File.separator + atlasmapCatalogFilesName));
                }
                else if (catEntryname.contains(".jar")) {
                    out = new BufferedOutputStream(new FileOutputStream(baseFolder + File.separator + catEntryname));
                }
                else {
                    continue;
                }
                LOG.debug("  Extracting ADM file entry '{}'", catEntryname);
                try {
                    int len = 0;
                    while ((len = zipIn.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                finally {
                    if (out != null) out.close();
                }
            }
            zipIn.close();

        } catch (IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Create a file in the mapping folder based on the specified file name containing the specified stream.
     *
     * @param fileName
     * @param mapping
     * @throws IOException
     */
    private void createMappingFile(String fileName, InputStream mapping) throws IOException {
        String outputFilePath = mappingFolder + File.separator + fileName;
        LOG.debug("Creating mapping file '{}'", outputFilePath);
        FileOutputStream outputStream = new FileOutputStream(outputFilePath);

        int length = 0;
        byte[] data = null;
        do {
            length = mapping.available();
            if (length <= 0) {
                break;
            }
            data = new byte[length];
            if ((mapping.read(data)) == -1) {
                break;
            }
            outputStream.write(data);
        } while (true);
        outputStream.close();
        mapping.close();
    }

    /**
     * Return a File object based on the specified format and ID.
     *
     * @param mappingFormat
     * @param mappingId
     * @return
     */
    private File getMappingFile(String mappingFormat, String mappingId) {
        File mappingFile = null;
        java.nio.file.Path mappingFilePath = null;

        if (mappingFormat.equals("JSON") || mappingFormat.equals("XML")) {
            mappingFilePath = Paths.get(mappingFolder + File.separator + generateMappingFileName(mappingId));
        }
        else {
            mappingFilePath = Paths.get(mappingFolder + File.separator + mappingId);
        }

        mappingFile = mappingFilePath.toFile();
        if (!mappingFile.exists()) {
            return null;
        }
        return mappingFile;
    }

    protected String generateMappingFileName(String mappingName) {
        return String.format("atlasmapping-%s.xml", mappingName);
    }

    protected byte[] toJson(Object value) {
        try {
            return Json.mapper().writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    protected <T> T fromJson(InputStream value, Class<T>clazz) {
        try {
            return Json.mapper().readValue(value, clazz);
        } catch (IOException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        }
    }

    protected <T> T fromXml(InputStream value, Class<T>clazz) {
        AtlasMapping atlasMapping = null;
        try {
            atlasMapping = getMappingFromStream(value);
        } catch (Exception e) {
            throw new WebApplicationException("Error retrieving mapping " + e.getMessage(), e);
        }
        return (T) atlasMapping;
    }
}
