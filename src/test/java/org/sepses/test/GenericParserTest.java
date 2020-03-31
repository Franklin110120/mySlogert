package org.sepses.test;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.sepses.processor.GenericParser;
import org.sepses.processor.Parser;
import org.sepses.yaml.Config;
import org.sepses.yaml.InternalConfig;
import org.sepses.yaml.InternalLogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

public class GenericParserTest {

    private static final Logger log = LoggerFactory.getLogger(GenericParserTest.class);

    static File templateFile;
    static File dataFile;
    static Parser parser;
    static Config config;

    @BeforeClass public static void setup() throws IOException {
        InputStream configIS = YamlParserTest.class.getClassLoader().getResourceAsStream("auth-config.yaml");
        InputStream templateIS = YamlParserTest.class.getClassLoader().getResourceAsStream("auth-template.yaml");
        InputStream is = new SequenceInputStream(configIS, templateIS);
        Yaml yaml = new Yaml(new Constructor(Config.class));
        config = yaml.load(is);

        InputStream iConfigIS = YamlParserTest.class.getClassLoader().getResourceAsStream("slogert.yaml");
        yaml = new Yaml(new Constructor(InternalConfig.class));
        InternalConfig internalConfig = yaml.load(iConfigIS);
        config.internalLogType = internalConfig.logTypes.stream().filter(item -> item.id.equals(config.logType)).findFirst().get();
    }

    @Test public void testAuthParser() throws IOException {
        templateFile =
                new File(GenericParserTest.class.getClassLoader().getResource("authlog_templates.csv").getFile());
        dataFile = new File(GenericParserTest.class.getClassLoader().getResource("authlog_structured.csv").getFile());
        config.logData = dataFile.getAbsolutePath();
        config.logTemplate = templateFile.getAbsolutePath();

        parser = new GenericParser(config);
        parser.generateOttrMap();
        parser.parseLogpaiData();
    }

    @Test public void testSyslogParser() throws IOException {
        templateFile =
                new File(GenericParserTest.class.getClassLoader().getResource("syslog_templates.csv").getFile());
        dataFile = new File(GenericParserTest.class.getClassLoader().getResource("syslog_structured.csv").getFile());
        config.logData = dataFile.getAbsolutePath();
        config.logTemplate = templateFile.getAbsolutePath();

        parser = new GenericParser(config);
        parser.generateOttrMap();
        parser.parseLogpaiData();
    }

    @Test public void testKernParser() throws IOException {
        templateFile = new File(GenericParserTest.class.getClassLoader().getResource("kern_templates.csv").getFile());
        dataFile = new File(GenericParserTest.class.getClassLoader().getResource("kern_structured.csv").getFile());
        config.logData = dataFile.getAbsolutePath();
        config.logTemplate = templateFile.getAbsolutePath();

        parser = new GenericParser(config);
        parser.generateOttrMap();
        parser.parseLogpaiData();
    }

    /**
     * not working at the moment ...
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Ignore @Test public void testLutra() throws IOException, InterruptedException {

        StringBuilder sbCommand = new StringBuilder();
        sbCommand.append("java -Xmx4096m -jar exe/lutra.jar");
        sbCommand.append(" --library auth-mapping.ottr --libraryFormat stottr ");
        sbCommand.append(" --inputFormat stottr auth-data.ottr ");
        sbCommand.append(" --mode expand --fetchMissing");
        String command = sbCommand.toString();
        System.out.println(command);

        Process proc = Runtime.getRuntime().exec(command);
        proc.waitFor();
        InputStream in = proc.getInputStream();
        InputStream err = proc.getErrorStream();
        byte b[] = new byte[in.available()];
        in.read(b, 0, b.length);
        System.out.println(new String(b));

        byte c[] = new byte[err.available()];
        err.read(c, 0, c.length);
        System.out.println(new String(c));
    }

}