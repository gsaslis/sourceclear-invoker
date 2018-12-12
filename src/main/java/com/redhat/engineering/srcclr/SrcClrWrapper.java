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
package com.redhat.engineering.srcclr;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.redhat.engineering.srcclr.converters.ProcessorConvertor;
import com.redhat.engineering.srcclr.converters.ThresholdConverter;
import com.redhat.engineering.srcclr.notification.EmailNotifier;
import com.redhat.engineering.srcclr.notification.Notifier;
import com.redhat.engineering.srcclr.processor.ProcessorResult;
import com.redhat.engineering.srcclr.processor.ScanResult;
import com.redhat.engineering.srcclr.utils.ManifestVersionProvider;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Set;
import java.util.concurrent.Callable;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Main entry point.
 */
@Command(name = "SrcClrWrapper",
                description = "Wrap SourceClear and invoke it.",
                mixinStandardHelpOptions = true, // add --help and --version options
                versionProvider = ManifestVersionProvider.class,
                subcommands = { SCM.class, Binary.class },
                defaultValueProvider = ConfigurationFileProvider.class)
@Getter
public class SrcClrWrapper implements Callable<Void>
{
    static final String UNMATCHED = " (unmatched args are passed directly to SourceClear)";

    private final Logger logger = LoggerFactory.getLogger( SrcClrWrapper.class );

    @Option( names = { "-d", "--debug" }, description = "Enable debug." )
    private boolean debug;

    @Option( names = { "-e", "--exception" }, description = "Throw exception on vulnerabilities found." )
    boolean exception = true;

    @Option( names = { "-t", "--threshold" }, converter=ThresholdConverter.class,
                    description = "Threshold on which exception is thrown. Only used with CVSS Processor")
    private int threshold = 0;

    @Option( names = { "-p", "--processor" }, defaultValue = "cvss", converter = ProcessorConvertor.class,
                    description = "Processor (cve|cvss) to use to analyse SourceClear results. Default is cvss")
    private ScanResult processor;

    @Option( names = { "-c", "--cpe" }, defaultValue="", description = "CPE (Product) Name")
    private String product;

    @Option( names = "--package", defaultValue="", description = "Package name. It's optional but required for RHOAR, e.g. (vertx|swarm|springboot) for RHOAR")
    private String packageName;

    @Option ( names = "--email-server", description = "SMTP Server to use to send notification email" )
    private String emailServer;

    @Option ( names = "--email-address", description = "Email address to notify. Domain portion will be used as FROM address")
    private String emailAddress;

    // TODO: Long term should support multiple types of notification.
    private Notifier notifier = new EmailNotifier();


    public static void main( String[] args ) throws Exception
    {
        try
        {
            new CommandLine( new SrcClrWrapper() ).parseWithHandler( new CommandLine.RunAll(), args );
        }
        catch ( CommandLine.ExecutionException e )
        {
            if ( e.getCause() != null && e.getCause() instanceof Exception )
            {
                throw (Exception)e.getCause();
            }
            throw e;
        }
    }

    @Override
    public Void call()
    {
        return null;
    }

    void enableDebug()
    {
        ch.qos.logback.classic.Logger rootLogger =
                        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );
        rootLogger.setLevel( Level.DEBUG );

        LoggerContext loggerContext = rootLogger.getLoggerContext();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext( loggerContext );
        encoder.setPattern( "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n" );
        encoder.start();

        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) rootLogger.getAppender( "STDOUT" );

        if ( appender != null )
        {
            appender.setContext( loggerContext );
            appender.setEncoder( encoder );
            appender.start();
        }
    }


    void notifyListeners( String scanInfo, Set<ProcessorResult> v )
    {
        if ( isNotEmpty ( emailAddress ) && isNotEmpty ( emailServer ) )
        {
            notifier.notify( this, scanInfo, v );
        }
    }
}
