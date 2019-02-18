/**
 * Copyright (C) 2019 Red Hat, Inc.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AtlasServiceInitializationTest {

    @Rule
    public TemporaryFolder temporaryFolderCreator = new TemporaryFolder();
    
    @Before
    public void before() throws IOException {
        File tmpFolder = temporaryFolderCreator.newFolder(AtlasServiceInitializationTest.class.getSimpleName());
        File admFile = new File(tmpFolder, "atlasmap-mapping.adm");
        Files.copy(AtlasServiceInitializationTest.class.getResourceAsStream("/atlasmap-mapping.adm"), admFile.toPath());
        System.setProperty(AtlasService.ATLASMAP_ADM_PATH, admFile.getCanonicalPath());
    }
    
    @After
    public void after() {
        System.clearProperty(AtlasService.ATLASMAP_ADM_PATH);
    }
    
    @Test
    public void testInitializationWithAdmFile() throws Exception {
        AtlasService atlasService = new AtlasService();
        Response listMappings = atlasService.listMappings(generateTestUriInfo("http://localhost:8686/v2/atlas", "http://localhost:8686/v2/atlas/mappings"), null);
        Object entity = listMappings.getEntity();
        String responseJson = new String((byte[])entity);
        System.out.println(responseJson);
        //TODO: check that it has been loaded correctly
}  

    protected UriInfo generateTestUriInfo(String baseUri, String absoluteUri) throws Exception {
        return new TestUriInfo(new URI(baseUri), new URI(absoluteUri));
    }

}
