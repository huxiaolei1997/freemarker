/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.freemarker.core.templateresolver;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.freemarker.core.Configuration;
import org.apache.freemarker.core.Template;
import org.apache.freemarker.core.templateresolver.impl.DefaultTemplateLookupStrategy;
import org.apache.freemarker.core.templateresolver.impl.DefaultTemplateNameFormat;
import org.apache.freemarker.core.templateresolver.impl.DefaultTemplateResolver;
import org.apache.freemarker.core.templateresolver.impl.StringTemplateLoader;
import org.apache.freemarker.core.templateresolver.impl.StrongCacheStorage;
import org.apache.freemarker.test.MonitoredTemplateLoader;
import org.apache.freemarker.test.MonitoredTemplateLoader.CloseSessionEvent;
import org.apache.freemarker.test.MonitoredTemplateLoader.CreateSessionEvent;
import org.apache.freemarker.test.MonitoredTemplateLoader.LoadEvent;
import org.hamcrest.Matchers;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class DefaultTemplateResolverTest {

    @Test
    public void testCachedException() throws Exception {
        MockTemplateLoader loader = new MockTemplateLoader();
        DefaultTemplateResolver tr = new DefaultTemplateResolver(
                loader, new StrongCacheStorage(),
                DefaultTemplateLookupStrategy.INSTANCE, DefaultTemplateNameFormat.INSTANCE,
                new Configuration(Configuration.VERSION_3_0_0));
        tr.setTemplateUpdateDelayMilliseconds(1000L);
        loader.setThrowException(true);
        try {
            tr.getTemplate("t", Locale.getDefault(), null).getTemplate();
            fail();
        } catch (IOException e) {
            assertEquals("mock IO exception", e.getMessage());
            assertEquals(1, loader.getLoadAttemptCount());
            try {
                tr.getTemplate("t", Locale.getDefault(), null).getTemplate();
                fail();
            } catch (IOException e2) {
                // Still 1 - returned cached exception
                assertThat(e2.getMessage(),
                        Matchers.allOf(Matchers.containsString("There was an error loading the template on an " +
                        "earlier attempt")));
                assertSame(e, e2.getCause());
                assertEquals(1, loader.getLoadAttemptCount());
                try {
                    Thread.sleep(1100L);
                    tr.getTemplate("t", Locale.getDefault(), null).getTemplate();
                    fail();
                } catch (IOException e3) {
                    // Cache had to retest
                    assertEquals("mock IO exception", e.getMessage());
                    assertEquals(2, loader.getLoadAttemptCount());
                }
            }
        }
    }
    
    @Test
    public void testCachedNotFound() throws Exception {
        MockTemplateLoader loader = new MockTemplateLoader();
        DefaultTemplateResolver cache = new DefaultTemplateResolver(
                loader, new StrongCacheStorage(), DefaultTemplateLookupStrategy.INSTANCE,
                DefaultTemplateNameFormat.INSTANCE, new Configuration());
        cache.setTemplateUpdateDelayMilliseconds(1000L);
        cache.setLocalizedLookup(false);
        assertNull(cache.getTemplate("t", Locale.getDefault(), null).getTemplate());
        assertEquals(1, loader.getLoadAttemptCount());
        assertNull(cache.getTemplate("t", Locale.getDefault(), null).getTemplate());
        // Still 1 - returned cached exception
        assertEquals(1, loader.getLoadAttemptCount());
        Thread.sleep(1100L);
        assertNull(cache.getTemplate("t", Locale.getDefault(), null).getTemplate());
        // Cache had to retest
        assertEquals(2, loader.getLoadAttemptCount());
    }

    private static class MockTemplateLoader implements TemplateLoader {
        private boolean throwException;
        private int loadAttemptCount; 
        
        public void setThrowException(boolean throwException) {
           this.throwException = throwException;
        }
        
        public int getLoadAttemptCount() {
            return loadAttemptCount;
        }
        
        @Override
        public TemplateLoaderSession createSession() {
            return null;
        }

        @Override
        public TemplateLoadingResult load(String name, TemplateLoadingSource ifSourceDiffersFrom,
                Serializable ifVersionDiffersFrom, TemplateLoaderSession session) throws IOException {
            ++loadAttemptCount;
            if (throwException) {
                throw new IOException("mock IO exception");
            }
            return TemplateLoadingResult.NOT_FOUND;
        }

        @Override
        public void resetState() {
            //
        }
        
    }
    
    @Test
    public void testManualRemovalPlain() throws IOException {
        Configuration cfg = new Configuration();
        cfg.setCacheStorage(new StrongCacheStorage());
        StringTemplateLoader loader = new StringTemplateLoader();
        cfg.setTemplateLoader(loader);
        cfg.setTemplateUpdateDelayMilliseconds(Integer.MAX_VALUE);
        
        loader.putTemplate("1.ftl", "1 v1");
        loader.putTemplate("2.ftl", "2 v1");
        assertEquals("1 v1", cfg.getTemplate("1.ftl").toString()); 
        assertEquals("2 v1", cfg.getTemplate("2.ftl").toString());
        
        loader.putTemplate("1.ftl", "1 v2");
        loader.putTemplate("2.ftl", "2 v2");
        assertEquals("1 v1", cfg.getTemplate("1.ftl").toString()); // no change 
        assertEquals("2 v1", cfg.getTemplate("2.ftl").toString()); // no change
        
        cfg.removeTemplateFromCache("1.ftl", cfg.getLocale(), null);
        assertEquals("1 v2", cfg.getTemplate("1.ftl").toString()); // changed 
        assertEquals("2 v1", cfg.getTemplate("2.ftl").toString());
        
        cfg.removeTemplateFromCache("2.ftl", cfg.getLocale(), null);
        assertEquals("1 v2", cfg.getTemplate("1.ftl").toString()); 
        assertEquals("2 v2", cfg.getTemplate("2.ftl").toString()); // changed
    }

    @Test
    public void testManualRemovalI18ed() throws IOException {
        Configuration cfg = new Configuration();
        cfg.setCacheStorage(new StrongCacheStorage());
        cfg.setLocale(Locale.US);
        StringTemplateLoader loader = new StringTemplateLoader();
        cfg.setTemplateLoader(loader);
        cfg.setTemplateUpdateDelayMilliseconds(Integer.MAX_VALUE);
        
        loader.putTemplate("1_en_US.ftl", "1_en_US v1");
        loader.putTemplate("1_en.ftl", "1_en v1");
        loader.putTemplate("1.ftl", "1 v1");
        
        assertEquals("1_en_US v1", cfg.getTemplate("1.ftl").toString());        
        assertEquals("1_en v1", cfg.getTemplate("1.ftl", Locale.UK).toString());        
        assertEquals("1 v1", cfg.getTemplate("1.ftl", Locale.GERMANY).toString());
        
        loader.putTemplate("1_en_US.ftl", "1_en_US v2");
        loader.putTemplate("1_en.ftl", "1_en v2");
        loader.putTemplate("1.ftl", "1 v2");
        assertEquals("1_en_US v1", cfg.getTemplate("1.ftl").toString());        
        assertEquals("1_en v1", cfg.getTemplate("1.ftl", Locale.UK).toString());        
        assertEquals("1 v1", cfg.getTemplate("1.ftl", Locale.GERMANY).toString());
        
        cfg.removeTemplateFromCache("1.ftl", cfg.getLocale(), null);
        assertEquals("1_en_US v2", cfg.getTemplate("1.ftl").toString());        
        assertEquals("1_en v1", cfg.getTemplate("1.ftl", Locale.UK).toString());        
        assertEquals("1 v1", cfg.getTemplate("1.ftl", Locale.GERMANY).toString());
        assertEquals("1 v2", cfg.getTemplate("1.ftl", Locale.ITALY).toString());
        
        cfg.removeTemplateFromCache("1.ftl", Locale.GERMANY, null);
        assertEquals("1_en v1", cfg.getTemplate("1.ftl", Locale.UK).toString());        
        assertEquals("1 v2", cfg.getTemplate("1.ftl", Locale.GERMANY).toString());

        cfg.removeTemplateFromCache("1.ftl", Locale.CANADA, null);
        assertEquals("1_en v1", cfg.getTemplate("1.ftl", Locale.UK).toString());
        
        cfg.removeTemplateFromCache("1.ftl", Locale.UK, null);
        assertEquals("1_en v2", cfg.getTemplate("1.ftl", Locale.UK).toString());        
    }

    @Test
    public void testZeroUpdateDelay() throws IOException, InterruptedException {
        Configuration cfg = new Configuration(Configuration.VERSION_3_0_0);
        cfg.setLocale(Locale.US);
        cfg.setCacheStorage(new StrongCacheStorage());
        MonitoredTemplateLoader loader = new MonitoredTemplateLoader();
        cfg.setTemplateLoader(loader);
        cfg.setTemplateUpdateDelayMilliseconds(0);
        for (int i = 1; i <= 3; i++) {
            loader.putTextTemplate("t.ftl", "v" + i);
            assertEquals("v" + i, cfg.getTemplate("t.ftl").toString());
        }

        loader.clearEvents();
        loader.putTextTemplate("t.ftl", "v8");
        assertEquals("v8", cfg.getTemplate("t.ftl").toString());
        assertEquals("v8", cfg.getTemplate("t.ftl").toString());
        loader.putTextTemplate("t.ftl", "v9");
        assertEquals("v9", cfg.getTemplate("t.ftl").toString());
        assertEquals("v9", cfg.getTemplate("t.ftl").toString());
        assertEquals(
                ImmutableList.of(
                        new LoadEvent("t_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND), // v8
                        new LoadEvent("t_en.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.OPENED),

                        new LoadEvent("t_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND), // v8
                        new LoadEvent("t_en.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED),
                        
                        new LoadEvent("t_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND), // v9
                        new LoadEvent("t_en.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.OPENED),

                        new LoadEvent("t_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND), // v9
                        new LoadEvent("t_en.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED)
                ),
                loader.getEvents(LoadEvent.class));
        
        cfg.setLocalizedLookup(false);
        loader.clearEvents();
        loader.putTextTemplate("t.ftl", "v10");
        assertEquals("v10", cfg.getTemplate("t.ftl").toString());
        loader.putTextTemplate("t.ftl", "v11"); // same time stamp, different content
        assertEquals("v11", cfg.getTemplate("t.ftl").toString());
        assertEquals("v11", cfg.getTemplate("t.ftl").toString());
        assertEquals("v11", cfg.getTemplate("t.ftl").toString());
        Thread.sleep(17L);
        assertEquals("v11", cfg.getTemplate("t.ftl").toString());
        loader.putTextTemplate("t.ftl", "v12");
        assertEquals("v12", cfg.getTemplate("t.ftl").toString());
        assertEquals("v12", cfg.getTemplate("t.ftl").toString());
        assertEquals(
                ImmutableList.of(
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.OPENED), // v10
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.OPENED), // v11
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED),
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.OPENED), // v12
                        new LoadEvent("t.ftl", TemplateLoadingResultStatus.NOT_MODIFIED)
                ),
                loader.getEvents(LoadEvent.class));
    }
    
    @Test
    public void testWrongEncodingReload() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_3_0_0);
        cfg.setLocale(Locale.US);
        cfg.setSourceEncoding(StandardCharsets.UTF_8);
        
        MonitoredTemplateLoader tl = new MonitoredTemplateLoader();
        tl.putBinaryTemplate("utf-8_en.ftl", "<#ftl encoding='utf-8'>Béka");
        tl.putBinaryTemplate("utf-8.ftl", "Bar");
        tl.putBinaryTemplate("iso-8859-1_en_US.ftl", "<#ftl encoding='ISO-8859-1'>Béka", StandardCharsets.ISO_8859_1,
                "v1");
        cfg.setTemplateLoader(tl);
        
        {
            Template t = cfg.getTemplate("utf-8.ftl");
            assertEquals("utf-8.ftl", t.getName());
            assertEquals("utf-8_en.ftl", t.getSourceName());
            assertEquals(StandardCharsets.UTF_8, t.getSourceEncoding());
            assertEquals("Béka", t.toString());
            
            assertEquals(
                    ImmutableList.of(
                            CreateSessionEvent.INSTANCE,
                            new LoadEvent("utf-8_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                            new LoadEvent("utf-8_en.ftl", TemplateLoadingResultStatus.OPENED),
                            CloseSessionEvent.INSTANCE),
                    tl.getEvents());
        }

        {
            tl.clearEvents();
            
            Template t = cfg.getTemplate("iso-8859-1.ftl");
            assertEquals("iso-8859-1.ftl", t.getName());
            assertEquals("iso-8859-1_en_US.ftl", t.getSourceName());
            assertEquals(StandardCharsets.ISO_8859_1, t.getSourceEncoding());
            assertEquals("Béka", t.toString());
            
            assertEquals(
                    ImmutableList.of(
                            CreateSessionEvent.INSTANCE,
                            new LoadEvent("iso-8859-1_en_US.ftl", TemplateLoadingResultStatus.OPENED),
                            CloseSessionEvent.INSTANCE),
                    tl.getEvents());
        }
    }

    @Test
    public void testNoWrongEncodingForTemplateLoader2WithReader() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_3_0_0);
        cfg.setLocale(Locale.US);
        cfg.setSourceEncoding(StandardCharsets.UTF_8);
        
        MonitoredTemplateLoader tl = new MonitoredTemplateLoader();
        tl.putTextTemplate("foo_en.ftl", "<#ftl encoding='utf-8'>ő");
        tl.putTextTemplate("foo.ftl", "B");
        cfg.setTemplateLoader(tl);
        
        {
            Template t = cfg.getTemplate("foo.ftl");
            assertEquals("foo.ftl", t.getName());
            assertEquals("foo_en.ftl", t.getSourceName());
            assertNull(t.getSourceEncoding());
            assertEquals("ő", t.toString());
            
            assertEquals(
                    ImmutableList.of(
                            CreateSessionEvent.INSTANCE,
                            new LoadEvent("foo_en_US.ftl", TemplateLoadingResultStatus.NOT_FOUND),
                            new LoadEvent("foo_en.ftl", TemplateLoadingResultStatus.OPENED),
                            CloseSessionEvent.INSTANCE),                
                    tl.getEvents());
        }
    }

    @Test
    public void testTemplateNameFormatException() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_3_0_0);
        cfg.setTemplateNameFormat(DefaultTemplateNameFormat.INSTANCE);
        try {
            cfg.getTemplate("../x");
            fail();
        } catch (MalformedTemplateNameException e) {
            // expected
        }
        try {
            cfg.getTemplate("\\x");
            fail();
        } catch (MalformedTemplateNameException e) {
            // expected
        }
    }

}
