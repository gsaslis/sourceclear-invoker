/*
 * Copyright (C) 2018 Red Hat, Inc.
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
package com.redhat.engineering.srcclr.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.engineering.srcclr.json.securitydata.AffectedRelease;
import com.redhat.engineering.srcclr.json.securitydata.PackageState;
import com.redhat.engineering.srcclr.json.securitydata.SecurityDataJSON;
import com.redhat.engineering.srcclr.utils.InternalException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.stream.Stream;

public class SecurityDataProcessor {
    private final static String REDHAT_SECURITY_DATA_CVE = "https://access.redhat.com/labs/securitydataapi/cve/CVE-";
    private final Logger logger = LoggerFactory.getLogger( getClass() );
    private String cpe;
    private String base_url = REDHAT_SECURITY_DATA_CVE;

    private String subpackage;

    public void setSubPackage(String subpackage)
    {
        this.subpackage = subpackage;
    }

    public String getSubPackage()
    {
        return subpackage;
    }

    public SecurityDataProcessor(String startCPE) 
    {
        cpe = startCPE;
    }

    public SecurityDataProcessor(String startCPE, String startBaseUrl)
    {
        cpe = startCPE;
        base_url = startBaseUrl;
    }

    private static String readAll(Reader rd) throws IOException 
    {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
          sb.append((char) cp);
        }
        return sb.toString();
    }

    private SecurityDataJSON lookUpAPI(String cve_id) throws IOException {
        String url = base_url + cve_id;

        logger.debug( "Looking up {}", url);

        HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();

        // Dealing with 406 error returns
        conn.setRequestProperty("Accept", "*/*");
    
        SecurityDataJSON json;

        try ( InputStream is = conn.getInputStream() )
        {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String jsonText = readAll(rd);

            ObjectMapper mapper = new ObjectMapper()
                            .configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false )
                            .configure( DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true );
            json = mapper.readValue( jsonText, SecurityDataJSON.class );

            return json;
        } 
    }

    public ProcessorResult process( String cve_id) throws InternalException
    {
        Boolean is_fail;
        
        ProcessorResult sdpr = new ProcessorResult();

        try
        {
            PackageState ps_found = null;

            SecurityDataJSON json = lookUpAPI(cve_id);

            if (StringUtils.isEmpty(subpackage))
            {
                ps_found = json.getPackageState() == null ? null :
                    json.getPackageState().stream()
                        .filter(ps -> cpe.equals(ps.getCpe()))
                        .findAny().orElse(null);
            }
            else
            {
                ps_found = json.getPackageState() == null ? null :
                    json.getPackageState().stream()
                        .filter(ps -> cpe.equals(ps.getCpe()))
                        .filter(ps -> subpackage.equals(ps.getPackageName()))
                        .findAny().orElse(null);

            }

            if (ps_found != null)
            {
                String fixed_state = ps_found.getFixState();

                if (Stream.of("will not fix", "not affected", "fix deferred").anyMatch(fixed_state::equalsIgnoreCase))
                {
                    is_fail = false;
                }
                else if (Stream.of("affected", "new").anyMatch(fixed_state::equalsIgnoreCase))
                {
                    sdpr.setMessage("fixed_state is " + fixed_state);
                    // setMessage or logger
                    is_fail = true;
                }
                else
                {
                    sdpr.setMessage("Unexpected fixed_state: " + fixed_state);
                    // setMessage or logger
                    is_fail = true;
                }
            }
            else
            {
                AffectedRelease ar_found = null;
                if (StringUtils.isEmpty(subpackage))
                {
                    ar_found = json.getAffectedRelease() == null ? null :
                        json.getAffectedRelease().stream()
                            .filter(ar -> cpe.equals(ar.getCpe()))
                            .findAny().orElse(null);
                }
                else
                {
                    ar_found = json.getAffectedRelease() == null ? null :
                        json.getAffectedRelease().stream()
                            .filter(ar -> cpe.equals(ar.getCpe()))
                            .filter(ar -> subpackage.equals(ar.getPackage()))
                            .findAny().orElse(null);
                }
                
                if (ar_found != null)
                {
                    sdpr.setMessage("AffectedRelease exists");
                    is_fail = true;
                }
                else
                {
                    sdpr.setMessage("No cpe exists");
                    is_fail = true;
                }
            }
        } 
        catch (FileNotFoundException e)  
        {
            logger.info("No CVE data in security data API. URL {}", e.getMessage());
            sdpr.setMessage("No CVE data in security data API");
            is_fail = true;
        }
        catch ( IOException e )
        {
            throw new InternalException( "Unable to process Security Data", e );
        }

        // if need to block
        if (is_fail)
        {
            sdpr.setFail(true);

            /* 
            * Currently, notification will be sent for every fails.
            * However in case we need a case that test fails but no notification is necessary, set to 'false'.
            */
            sdpr.setNotify(true);
        }

        return sdpr;

    }
}  