package nl.codeyellow;
/*
 * Copyright 2009-2010 Data Archiving and Networked Services (DANS), Netherlands.
 *
 * This file is part of DANS DataPerfect Library.
 *
 * DANS DataPerfect Library is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * DANS DataPerfect Library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with DANS DataPerfect
 * Library. If not, see <http://www.gnu.org/licenses/>.
 */
import nl.knaw.dans.common.dataperfect.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * This program shows how you can use DANS DataPerfect Library. This program exports a DataPerfect
 * database to an SQL script that can be read by MySQL to create a database with the same tables and
 * data.
 *
 * Please note that this program has not been thoroughly tested. However it could be the basis for a
 * more elaborate export program.
 *
 * To compile this program, open a command prompt and go to the directory of this file, then
 * execute:
 *
 * javac -cp dans-dp-lib.jar Dp2MySqlExport.java
 *
 * Of course dans-dp-lib.jar must be present in the same directory, or you must adjust the path in
 * the command line.
 *
 * @author Martin Braaksma
 * @author Jan van Mansum
 *
 */
public class DataPerfect
{
    public static void main(String[] args) throws IOException, DataPerfectLibException, NoSuchRecordFieldException
    {
        if (args.length < 2)
        {
            throw new IllegalArgumentException(
                    "Usage: Dp2MySqlExport <structure file> <output directory>");
        }

        final Database database = new Database(new File(args[0]) /*, "IBM437" (default) */);

        try {
            database.open();
            database.getPanels().stream()
                    .map(panel -> {
                        Table table =  DataPerfect.createTable(panel);
                        fillTable(table, panel);
                        return table;
                    }).forEach(table -> {
                        // Only save non empty table
                        table.save(args[1]);
                    });
        } finally {
            database.close();
        }
    }

    /**
     * Struct containing a csv table data;
     */
    static class Table
    {
        String tableName;

        int counter = 0;

        ArrayList<String> columnNames = new ArrayList<>();
        ArrayList<ArrayList<String>> rows = new ArrayList<>();

        public void addColumn(String columnName) {
            if (columnName != null) {
                columnNames.add(columnName);
            } else {
                columnNames.add("unknown_" + (counter++));
            }
        }

        public void save(String directory) {
            try {
                FileWriter file = new FileWriter(directory + "/" +  tableName + ".csv");
                CSVFormat format = CSVFormat.RFC4180.withHeader(
                        this.columnNames.toArray(
                                new String[columnNames.size()]
                        )
                );

                CSVPrinter csvFilePrinter = new CSVPrinter(file, format);

                rows.forEach(row -> {
                    try {
                        csvFilePrinter.printRecord(row);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                file.flush();
                file.close();
                csvFilePrinter.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    private static Table createTable(final Panel panel) {
        final String tableName = panel.getFile().getName();
        Table table = new Table();
        table.tableName = tableName;


        panel.getFields().stream()
                .map(field -> {
                    if (field.getName() != null) {
                        return field.getName();
                    } else {
                        return ""+field.getNumber();
                    }
                })
                .forEach(fieldname -> table.addColumn(fieldname));

        return table;
    }

    private static void fillTable(Table table, Panel panel)
    {
        final Iterator<Record> recordIterator = panel.recordIterator();
        final List<Field> fields = panel.getFields();


        panel.recordIterator().forEachRemaining(record -> {
            ArrayList<String> values = new ArrayList<>();

            fields.stream().forEach(field -> {
                String value = null;
                try {
                    switch (field.getType()) {
                        case D:

                            value = getDate(record.getValueAsNumber(field.getNumber()).intValue());

                            break;
                        case T:
                            value = getTime(record.getValueAsNumber(field.getNumber()).intValue());
                            break;
                        default:
                            value  = record.getValueAsString(field.getNumber());
                    }
                } catch (NoSuchRecordFieldException e) {
                    e.printStackTrace();
                }

                values.add(value);
            });

            table.rows.add(values);
        });



    }

    private static String getTime(final int numberOfSecondsSinceMidnight)
    {
        final Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, numberOfSecondsSinceMidnight);

        return calendar.get(Calendar.HOUR) + ":" + calendar.get(Calendar.MINUTE) + ":"
                + calendar.get(Calendar.SECOND);
    }

    private static String getDate(final int numberOfDaysSinceDPDateOffset)
    {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(1900, Calendar.MARCH, 1);
        calendar.add(Calendar.DAY_OF_MONTH, numberOfDaysSinceDPDateOffset);

        return calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-"
                + calendar.get(Calendar.DAY_OF_MONTH);
    }
}
