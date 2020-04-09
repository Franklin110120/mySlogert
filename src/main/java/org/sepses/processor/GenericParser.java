package org.sepses.processor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.sepses.helper.*;
import org.sepses.yaml.Config;
import org.sepses.yaml.ConfigParameter;
import org.sepses.yaml.YamlFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class GenericParser implements Parser {

    private static final Logger log = LoggerFactory.getLogger(GenericParser.class);

    private static final String[] TEMPLATE_LOGPAI = { "EventId", "EventTemplate", "Occurrences" };
    private static final String[] TEMPLATE_SLOGERT = { "Hash", "EventTemplate", "OttrID", "Parameters", "Keywords" };

    private final Map<String, Template> hashTemplates;
    private final Config config;
    private final HashMap<String, ConfigParameter> parameterMap = new HashMap<>();
    private final Dataset templateDS;

    public GenericParser(Config config) throws IOException {
        templateDS = DatasetFactory.create();

        // init regexNER & OTTR template
        YamlFunction.constructRegexNer(config);
        YamlFunction.constructOttrTemplate(config);

        // initialization
        hashTemplates = new HashMap<>();
        this.config = config;

        // add NER and non-NER parameters
        config.nerParameters.forEach(item -> parameterMap.put(item.id, item));
        config.nonNerParameters.forEach(item -> parameterMap.put(item.id, item));

        // parsing & update template
        createOrUpdateTemplate();

        templateDS.close();
    }

    /**
     * *** extract additional hashTemplates from input log (data+hashTemplates) if possible
     *
     * @param logpaiStructure
     * @param inputData
     */
    private void extractTemplate(Iterable<CSVRecord> logpaiStructure, List<LogLine> inputData) {

        // *** Annotate template nerParameters
        for (CSVRecord templateCandidate : logpaiStructure) {
            try {
                String logpaiEventId = templateCandidate.get(TEMPLATE_LOGPAI[0]);
                String logpaiTemplate = templateCandidate.get(TEMPLATE_LOGPAI[1]);
                String hashCandidate = Utility.createHash(logpaiTemplate);

                if (!hashTemplates.containsKey(hashCandidate)) {
                    // *** Generate new template
                    for (LogLine logLine : inputData) {
                        // ** Find logLine with the corresponding template
                        if (logpaiEventId.equals(logLine.getLogpaiEventId())) {
                            if (!hashCandidate.equals(logLine.getTemplateHash())) {
                                log.warn("logpai-id: " + logpaiEventId + " template is not consistent!");
                                hashCandidate = logLine.getTemplateHash();
                            }
                            Template template = new Template(logpaiTemplate, hashCandidate, logLine, config);
                            hashTemplates.put(hashCandidate, template);
                            break;
                        }
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                log.error(e.getMessage());
            }
        }
    }


    private void saveTemplates() {

    }

    private void writeTemplates() throws IOException {

        String location = config.targetTemplate;
        RDFDataMgr.write(new FileOutputStream(location), templateDS, RDFFormat.TRIG);

    }

    @Override public void createOrUpdateTemplate() throws IOException {

        // *** load existing hashTemplates
        if (!config.isOverride) {
            String logpaiTemplateString = getClass().getClassLoader().getResource(config.logBaseTemplate).getFile();
            if (!logpaiTemplateString.isEmpty()) {
                File logpaiTemplate = new File(logpaiTemplateString);
                Reader reader = new FileReader(logpaiTemplate);
                Iterable<CSVRecord> readerIterator = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
                hashTemplates.putAll(Utility.loadTemplates(readerIterator, config));
                reader.close();
            }
        }

        // *** read and collect input logpai structure
        Reader templateReader = new FileReader(Paths.get(config.logTemplate).toFile());
        Iterable<CSVRecord> inputTemplates = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(templateReader);
        // *** read and collect input logpai data
        Reader dataReader = new FileReader(Paths.get(config.logData).toFile());
        Iterable<CSVRecord> inputData = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(dataReader);
        List<LogLine> logLines = new ArrayList<>();
        inputData.forEach(inputRow -> logLines.add(createLogLine(inputRow)));

        // *** derive hashTemplates
        extractTemplate(inputTemplates, logLines);

        // *** write templates
        writeTemplates();

        // *** close readers
        templateReader.close();
        dataReader.close();
    }

    @Override public void generateOttrMap() throws IOException {
        StringBuilder sb = new StringBuilder();

        // *** load template
        InputStream is = new FileInputStream(config.targetOttr);
        try {
            String baseTemplate = IOUtils.toString(is, Charset.defaultCharset());
            sb.append(baseTemplate);
            sb.append(System.lineSeparator()).append(System.lineSeparator());

            hashTemplates.values().stream().forEach(template -> {
                sb.append(template.ottrTemplate);
                sb.append(System.lineSeparator());
            });
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Utility.writeToFile(sb.toString(), config.targetTemplate);
    }

    @Override public void parseLogpaiData() throws IOException {

        // *** read and collect input logpai data
        Reader dataReader = new FileReader(Paths.get(config.logData).toFile());
        Iterable<CSVRecord> inputData = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(dataReader);
        List<LogLine> logLines = new ArrayList<>();
        inputData.forEach(inputRow -> logLines.add(createLogLine(inputRow)));
        dataReader.close();

        StringBuilder sb = new StringBuilder();
        config.ottrNS.forEach(ns -> {
            sb.append("@prefix " + ns.prefix + ": <" + ns.uri + "> .").append(System.lineSeparator());
        });
        sb.append(System.lineSeparator());

        logLines.forEach(logLine -> {
            Template template = hashTemplates.get(logLine.getTemplateHash());

            // === this should be moved to the template RDF
            //            String keywords = "()";
            //            if (!template.keywords.isEmpty()) {
            //                keywords = "(\"" + template.keywords.stream().
            //                        map(Object::toString).
            //                        collect(Collectors.joining("\",\"")).toString() + "\")";
            //            }
            //            sb.append(keywords).append(",\"");

            sb.append(template.ottrId);
            sb.append("(").append(Template.BASE_OTTR_ID).append(UUID.randomUUID()).append(",\"");
            sb.append(logLine.getDateTime()).append("\",\"");
            sb.append(Utility.cleanContent(logLine.getContent())).append("\",\"");
            sb.append(logLine.getTemplateHash()).append("\",\"");

            // log-specific params
            config.internalLogType.components.stream().forEach(item -> {
                String value = logLine.getSpecialParameters().get(item.column);
                if (item.ottr.ottrType.equals("ottr:IRI")) {
                    sb.delete(sb.length() - 1, sb.length());
                    sb.append(value).append(",\"");
                } else {
                    sb.append(value).append("\",\"");
                }
            });

            for (int i = 0; i < template.parameters.size(); i++) {
                String paramString = logLine.getParameters().get(i);
                String paramType = template.parameters.get(i);
                ConfigParameter parameter = parameterMap.get(paramType);
                if (paramType.equals(Template.UNKNOWN_PARAMETER) || !parameter.ottr.ottrType
                        .equals(Utility.OTTR_IRI)) {
                    sb.append(Utility.cleanContent(paramString)).append("\",\"");
                } else {
                    sb.delete(sb.length() - 1, sb.length());
                    paramString = parameter.ottr.ottrPrefix + ":" + Utility.cleanUriContent(paramString);
                    sb.append(paramString).append(",\"");
                }
            }

            sb.delete(sb.length() - 2, sb.length());
            sb.append(") .").append(System.lineSeparator());
        });

        Utility.writeToFile(sb.toString(), config.targetData);
    }

    /**
     * creation of a logline
     * TODO: Check and update!
     *
     * @param record
     * @return
     * @throws NoSuchAlgorithmException
     */
    private LogLine createLogLine(CSVRecord record) {
        LogLine logline;

        try {
            if (config.logType.equals("unix")) {
                logline = new LogLineUnix(record, config.internalLogType);
            } else if (config.logType.equals("audit")) {
                logline = new LogLineAudit(record, config.internalLogType);
            } else if (config.logType.equals("ftp")) {
                logline = new LogLineFTP(record, config.internalLogType);
            } else if (config.logType.equals("apache")) {
                logline = new LogLineApache(record, config.internalLogType);
            } else {
                logline = null;
            }
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage());
            logline = null;
        }

        return logline;
    }
}
