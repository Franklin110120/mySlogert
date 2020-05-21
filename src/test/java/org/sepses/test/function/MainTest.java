package org.sepses.test.function;

import org.apache.commons.cli.ParseException;
import org.junit.Test;
import org.sepses.MainParser;

import java.io.IOException;

public class MainTest {

    String[] parameters = { "-c src/main/resources/config-io.yaml" };

    @Test public void TestMainParser() throws IOException, ParseException {
        MainParser.main(parameters);
    }
}
