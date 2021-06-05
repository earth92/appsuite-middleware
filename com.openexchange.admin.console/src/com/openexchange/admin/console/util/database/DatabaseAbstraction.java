/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.admin.console.util.database;

import static com.openexchange.java.Autoboxing.I;
import java.net.URI;
import java.net.URISyntaxException;
import com.openexchange.admin.console.AdminParser;
import com.openexchange.admin.console.AdminParser.NeededQuadState;
import com.openexchange.admin.console.CLIOption;
import com.openexchange.admin.console.util.UtilAbstraction;
import com.openexchange.admin.rmi.dataobjects.Database;
import com.openexchange.admin.rmi.exceptions.InvalidDataException;
import com.openexchange.java.Strings;

/**
 * This is an abstract class for all common attributes and methods of database related command line tools
 *
 * @author d7
 */
public abstract class DatabaseAbstraction extends UtilAbstraction {

    protected static final char OPT_NAME_DATABASE_ID_SHORT = 'i';

    protected static final String OPT_NAME_DATABASE_ID_LONG = "id";

    protected final static char OPT_NAME_DB_USERNAME_SHORT = 'u';

    protected final static String OPT_NAME_DB_USERNAME_LONG = "dbuser";

    protected final static char OPT_NAME_DBNAME_SHORT = 'n';

    protected final static String OPT_NAME_DBNAME_LONG = "name";

    protected final static char OPT_NAME_DB_PASSWD_SHORT = 'p';

    protected final static String OPT_NAME_DB_PASSWD_LONG = "dbpasswd";

    protected final static char OPT_NAME_POOL_HARDLIMIT_SHORT = 'l';

    protected final static String OPT_NAME_POOL_HARDLIMIT_LONG = "poolhardlimit";

    protected final static char OPT_NAME_POOL_INITIAL_SHORT = 'o';

    protected final static String OPT_NAME_POOL_INITIAL_LONG = "poolinitial";

    protected final static char OPT_NAME_POOL_MAX_SHORT = 'a';

    protected final static String OPT_NAME_POOL_MAX_LONG = "poolmax";

    protected final static char OPT_NAME_DB_DRIVER_SHORT = 'd';

    protected final static String OPT_NAME_DB_DRIVER_LONG = "dbdriver";

    protected final static char OPT_NAME_MASTER_ID_SHORT = 'M';

    protected final static String OPT_NAME_MASTER_ID_LONG = "masterid";

    protected final static char OPT_NAME_MAX_UNITS_SHORT = 'x';

    protected final static String OPT_NAME_MAX_UNITS_LONG = "maxunit";

    protected final static char OPT_NAME_HOSTNAME_SHORT = 'H';

    protected final static String OPT_NAME_HOSTNAME_LONG = "hostname";

    protected final static char OPT_NAME_IS_MASTER_SHORT = 'm';

    protected final static String OPT_NAME_IS_MASTER_LONG = "master";

    protected final static String OPT_NAME_CREATE_SCHMEMAS_LONG = "create-userdb-schemas";

    protected final static String OPT_NAME_NUMBER_OF_SCHMEMAS_LONG = "userdb-schema-count";

    protected final static String OPT_NAME_NUMBER_OF_SCHMEMAS_TO_KEEP_LONG = "schemas-to-keep";

    protected final static String OPT_NAME_ONLY_EMPTY_SCHEMAS_LONG = "only-empty-schemas";

    protected final static String OPT_NAME_SCHEMA_LONG = "schema";

    protected CLIOption databaseIdOption = null;

    protected CLIOption databaseUsernameOption = null;

    protected CLIOption databaseDriverOption = null;

    protected CLIOption databasePasswdOption = null;

    protected CLIOption databaseIsMasterOption = null;

    protected CLIOption databaseMasterIDOption = null;

    protected CLIOption databaseNameOption = null;

    protected CLIOption hostnameOption = null;

    protected CLIOption maxUnitsOption = null;

    protected CLIOption poolHardlimitOption = null;

    protected CLIOption poolInitialOption = null;

    protected CLIOption poolMaxOption = null;

    protected CLIOption schemaOption = null;

    protected CLIOption createSchemasOption = null;

    protected CLIOption numberOfSchemasOption = null;

    protected CLIOption schemasToKeepOption = null;

    protected CLIOption onlyEmptySchemasOption = null;

    // Needed for right error output
    protected String dbid = null;
    protected String dbname = null;

    protected Boolean createSchemas;
    protected Integer numberOfSchemas;

    protected Integer schemasToKeep;

    protected Boolean onlyEmptySchemas;

    protected void parseAndSetCreateAndNumberOfSchemas(final AdminParser parser) throws InvalidDataException {
        if (null == parser.getOptionValue(this.createSchemasOption)) {
            createSchemas = Boolean.FALSE;
            numberOfSchemas = Integer.valueOf(0);
            return;
        }

        createSchemas = Boolean.TRUE;

        String tmp = (String) parser.getOptionValue(this.numberOfSchemasOption);
        if (createSchemas.booleanValue()) {
            if (Strings.isEmpty(tmp)) {
                numberOfSchemas = Integer.valueOf(0);
            } else {
                try {
                    numberOfSchemas = Integer.valueOf(tmp.trim());
                } catch (NumberFormatException e) {
                    throw new InvalidDataException("Invalid value specified for \"" + OPT_NAME_NUMBER_OF_SCHMEMAS_LONG + "\" option. Should be a number.", e);
                }
            }
        } else {
            if (Strings.isNotEmpty(tmp)) {
                throw new InvalidDataException("\"" + OPT_NAME_NUMBER_OF_SCHMEMAS_LONG + "\" option can only be set, if \"" + OPT_NAME_CREATE_SCHMEMAS_LONG + "\" is set to \"true\"");
            }
            numberOfSchemas = Integer.valueOf(0);
        }
    }

    protected void parseAndSetNumberOfSchemas(final AdminParser parser) throws InvalidDataException {
        String tmp = (String) parser.getOptionValue(this.numberOfSchemasOption);
        if (Strings.isEmpty(tmp)) {
            numberOfSchemas = Integer.valueOf(0);
        } else {
            try {
                numberOfSchemas = Integer.valueOf(tmp.trim());
            } catch (NumberFormatException e) {
                throw new InvalidDataException("Invalid value specified for \"" + OPT_NAME_NUMBER_OF_SCHMEMAS_LONG + "\" option. Should be a number.", e);
            }
        }
    }

    protected void parseAndSetSchemasToKeep(final AdminParser parser) throws InvalidDataException {
        String tmp = (String) parser.getOptionValue(this.schemasToKeepOption);
        if (Strings.isEmpty(tmp)) {
            schemasToKeep = Integer.valueOf(0);
        } else {
            try {
                schemasToKeep = Integer.valueOf(tmp.trim());
            } catch (NumberFormatException e) {
                throw new InvalidDataException("Invalid value specified for \"" + OPT_NAME_NUMBER_OF_SCHMEMAS_TO_KEEP_LONG + "\" option. Should be a number.", e);
            }
        }
    }

    protected void parseAndSetOnlyEmptySchemas(final AdminParser parser) {
        if (null != parser.getOptionValue(this.onlyEmptySchemasOption)) {
            onlyEmptySchemas = Boolean.TRUE;
        } else {
            onlyEmptySchemas = Boolean.FALSE;
        }
    }

    protected void parseAndSetDatabaseID(final AdminParser parser, final Database db) {
        dbid = (String) parser.getOptionValue(this.databaseIdOption);
        if (null != dbid) {
            db.setId(Integer.valueOf(dbid));
        }
    }

    protected void parseAndSetDatabasename(final AdminParser parser, final Database db) {
        dbname = (String) parser.getOptionValue(this.databaseNameOption);
        if (null != dbname) {
            db.setName(dbname);
        }
    }

    protected void parseAndSetSchema(final AdminParser parser, final Database db) {
        String schema = (String) parser.getOptionValue(this.schemaOption);
        if (null != schema) {
            db.setScheme(schema);
        }
    }

    private void parseAndSetHostname(final AdminParser parser, final Database db) throws InvalidDataException {
        String hostname = (String) parser.getOptionValue(this.hostnameOption);
        if (null != hostname) {
            if (hostname.startsWith("mysql://")) {
                URI uri;
                try {
                    uri = new URI(hostname);
                    int port = uri.getPort();
                    hostname = uri.getHost() + String.valueOf(port != -1 ? port : 3306);
                } catch (URISyntaxException e) {
                    throw new InvalidDataException(e);
                }
            }
            db.setUrl("jdbc:mysql://" + hostname);
        }
    }

    private void parseAndSetPasswd(final AdminParser parser, final Database db) {
        final String passwd = (String) parser.getOptionValue(this.databasePasswdOption);
        if (null != passwd) {
            db.setPassword(passwd);
        }
    }

    private void parseAndSetPoolmax(final AdminParser parser, final Database db) {
        final String pool_max = (String) parser.getOptionValue(this.poolMaxOption);
        if (pool_max != null) {
            db.setPoolMax(Integer.valueOf(pool_max));
        }
    }

    private void parseAndSetPoolInitial(final AdminParser parser, final Database db) {
        final String pool_initial = (String) parser.getOptionValue(this.poolInitialOption);
        if (null != pool_initial) {
            db.setPoolInitial(Integer.valueOf(pool_initial));
        }
    }

    private void parseAndSetPoolHardLimit(final AdminParser parser, final Database db) throws InvalidDataException {
        final String pool_hard_limit = (String) parser.getOptionValue(this.poolHardlimitOption);
        if (pool_hard_limit != null) {
            if (!pool_hard_limit.matches("true|false")) {
                throw new InvalidDataException("Only true or false are allowed for " + OPT_NAME_POOL_HARDLIMIT_LONG);
            }
            db.setPoolHardLimit(I(Boolean.parseBoolean(pool_hard_limit) ? 1 : 0));
        }
    }

    private void parseAndSetMaxUnits(final AdminParser parser, final Database db) {
        final String maxunits = (String) parser.getOptionValue(this.maxUnitsOption);
        if (maxunits != null) {
            db.setMaxUnits(Integer.valueOf(maxunits));
        }
    }

    private void parseAndSetDBUsername(final AdminParser parser, final Database db) {
        final String username = (String) parser.getOptionValue(this.databaseUsernameOption);
        if (null != username) {
            db.setLogin(username);
        }
    }

    private void parseAndSetDriver(final AdminParser parser, final Database db) {
        final String driver = (String) parser.getOptionValue(this.databaseDriverOption);
        if (driver != null) {
            db.setDriver(driver);
        }
    }

    protected void parseAndSetMasterAndID(final AdminParser parser, final Database db) throws InvalidDataException {
        Boolean ismaster = null;
        final String databaseismaster = (String) parser.getOptionValue(this.databaseIsMasterOption);
        if (databaseismaster != null) {
            ismaster = Boolean.valueOf(databaseismaster);
            db.setMaster(ismaster);
        }
        final String databasemasterid = (String) parser.getOptionValue(this.databaseMasterIDOption);
        if (null != ismaster && false == ismaster.booleanValue()) {
            if (databasemasterid != null) {
                db.setMasterId(Integer.valueOf(databasemasterid));
            } else {
                printError(null, null, "master id must be set if this database isn't the master", parser);
                parser.printUsage();
                sysexit(SYSEXIT_MISSING_OPTION);
            }
        } else if (null == ismaster || true == ismaster.booleanValue()) {
            if (databasemasterid != null) {
                throw new InvalidDataException("Master ID can only be set if this is a slave.");
            }
        }
    }

    protected void setCreateAndNumberOfSchemasOption(final AdminParser parser) {
        this.createSchemasOption = setLongOpt(parser, OPT_NAME_CREATE_SCHMEMAS_LONG, "A flag that signals whether userdb schemas are supposed to be pre-created", false, false);
        this.numberOfSchemasOption = setLongOpt(parser, OPT_NAME_NUMBER_OF_SCHMEMAS_LONG, "(Optionally) Specifies the number of userdb schemas that are supposed to be pre-created. If missing, number of schemas is calculated by \"" + OPT_NAME_MAX_UNITS_LONG + "\" divided by CONTEXTS_PER_SCHEMA config option from hosting.properties", true, false);
    }

    protected void setNumberOfSchemasOption(final AdminParser parser) {
        this.numberOfSchemasOption = setLongOpt(parser, OPT_NAME_NUMBER_OF_SCHMEMAS_LONG, "(Optionally) Specifies the number of userdb schemas that are supposed to be pre-created. If missing, number of schemas is calculated by \"" + OPT_NAME_MAX_UNITS_LONG + "\" divided by CONTEXTS_PER_SCHEMA config option from hosting.properties", true, false);
    }

    protected void setSchemasToKeepOption(final AdminParser parser) {
        this.schemasToKeepOption = setLongOpt(parser, OPT_NAME_NUMBER_OF_SCHMEMAS_TO_KEEP_LONG, "(Optionally) Specifies the number of empty schemas that are supposed to be kept (per database host). If missing, all empty schemas are attempted to be deleted. Ineffective if \"" + OPT_NAME_SCHEMA_LONG + "\" option is specified", true, false);
    }

    protected void setOnlyEmptySchemas(final AdminParser parser) {
        this.onlyEmptySchemasOption = setLongOpt(parser, OPT_NAME_ONLY_EMPTY_SCHEMAS_LONG, "(Optionally) Specifies to list only empty schemas (per database host). If missing, all empty schemas are considered", false, false);
    }

    protected void setDatabaseIDOption(final AdminParser parser) {
        setDatabaseIDOption(parser, NeededQuadState.eitheror, "The id of the database.");
    }

    protected void setDatabaseIDOption(final AdminParser parser, NeededQuadState state, String description) {
        this.databaseIdOption = setShortLongOpt(parser, OPT_NAME_DATABASE_ID_SHORT, OPT_NAME_DATABASE_ID_LONG, description, true, state);
    }

    protected void setDatabaseSchemaOption(final AdminParser parser) {
        setDatabaseSchemaOption(parser, false);
    }

    protected void setDatabaseSchemaOption(final AdminParser parser, boolean required) {
        this.schemaOption = setLongOpt(parser, OPT_NAME_SCHEMA_LONG, "The optional schema name of the database.", true, required);
    }

    protected void setDatabasePoolMaxOption(final AdminParser parser, final String defaultvalue, final boolean required) {
        if (null != defaultvalue) {
            this.poolMaxOption = setShortLongOptWithDefault(
                parser,
                OPT_NAME_POOL_MAX_SHORT,
                OPT_NAME_POOL_MAX_LONG,
                "Db pool max",
                defaultvalue,
                true,
                convertBooleantoTriState(required));
        } else {
            this.poolMaxOption = setShortLongOpt(
                parser,
                OPT_NAME_POOL_MAX_SHORT,
                OPT_NAME_POOL_MAX_LONG,
                "Db pool max",
                true,
                convertBooleantoTriState(required));
        }
    }

    protected void setDatabasePoolInitialOption(final AdminParser parser, final String defaultvalue, final boolean required) {
        if (null != defaultvalue) {
            this.poolInitialOption = setShortLongOptWithDefault(
                parser,
                OPT_NAME_POOL_INITIAL_SHORT,
                OPT_NAME_POOL_INITIAL_LONG,
                "Db pool initial",
                defaultvalue,
                true,
                convertBooleantoTriState(required));
        } else {
            this.poolInitialOption = setShortLongOpt(
                parser,
                OPT_NAME_POOL_INITIAL_SHORT,
                OPT_NAME_POOL_INITIAL_LONG,
                "Db pool initial",
                true,
                convertBooleantoTriState(required));
        }
    }

    protected void setDatabaseHostnameOption(final AdminParser parser, final boolean required) {
        this.hostnameOption = setShortLongOpt(
            parser,
            OPT_NAME_HOSTNAME_SHORT,
            OPT_NAME_HOSTNAME_LONG,
            "Hostname of the server",
            true,
            convertBooleantoTriState(required));
    }

    protected void setDatabaseUsernameOption(final AdminParser parser, final boolean required) {
        this.databaseUsernameOption = setShortLongOpt(
            parser,
            OPT_NAME_DB_USERNAME_SHORT,
            OPT_NAME_DB_USERNAME_LONG,
            "Name of the user for the database",
            true,
            convertBooleantoTriState(required));
    }

    protected void setDatabaseDriverOption(final AdminParser parser, final String defaultvalue, final boolean required) {
        if (null != defaultvalue) {
            this.databaseDriverOption = setShortLongOptWithDefault(
                parser,
                OPT_NAME_DB_DRIVER_SHORT,
                OPT_NAME_DB_DRIVER_LONG,
                "The driver to be used for the database",
                defaultvalue,
                true,
                convertBooleantoTriState(required));
        } else {
            this.databaseDriverOption = setShortLongOpt(
                parser,
                OPT_NAME_DB_DRIVER_SHORT,
                OPT_NAME_DB_DRIVER_LONG,
                "The driver to be used for the database",
                true,
                convertBooleantoTriState(required));
        }
    }

    protected void setDatabasePasswdOption(final AdminParser parser, final boolean required) {
        this.databasePasswdOption = setShortLongOpt(
            parser,
            OPT_NAME_DB_PASSWD_SHORT,
            OPT_NAME_DB_PASSWD_LONG,
            "Password for the database",
            true,
            convertBooleantoTriState(required));
    }

    protected void setDatabaseIsMasterOption(final AdminParser parser, final boolean required) {
        this.databaseIsMasterOption = setShortLongOpt(
            parser,
            OPT_NAME_IS_MASTER_SHORT,
            OPT_NAME_IS_MASTER_LONG,
            "true/false",
            "Set this if the registered database is the master",
            required);
    }

    protected void setDatabaseMasterIDOption(final AdminParser parser, final boolean required) {
        this.databaseMasterIDOption = setShortLongOpt(
            parser,
            OPT_NAME_MASTER_ID_SHORT,
            OPT_NAME_MASTER_ID_LONG,
            "If this database isn't the master give the id of the master here",
            true,
            convertBooleantoTriState(required));
    }

    protected void setDatabaseMaxUnitsOption(final AdminParser parser, final String defaultvalue, final boolean required) {
        if (null != defaultvalue) {
            this.maxUnitsOption = setShortLongOptWithDefault(
                parser,
                OPT_NAME_MAX_UNITS_SHORT,
                OPT_NAME_MAX_UNITS_LONG,
                "The maximum number of contexts in this database.",
                defaultvalue,
                true,
                convertBooleantoTriState(required));
        } else {
            this.maxUnitsOption = setShortLongOpt(
                parser,
                OPT_NAME_MAX_UNITS_SHORT,
                OPT_NAME_MAX_UNITS_LONG,
                "The maximum number of contexts in this database.",
                true,
                convertBooleantoTriState(required));
        }
    }

    protected void setDatabasePoolHardlimitOption(final AdminParser parser, final String defaultvalue, final boolean required) {
        if (null != defaultvalue) {
            // FIXME: choeger Enter right description here
            this.poolHardlimitOption = setShortLongOptWithDefault(
                parser,
                OPT_NAME_POOL_HARDLIMIT_SHORT,
                OPT_NAME_POOL_HARDLIMIT_LONG,
                "true/false",
                "Db pool hardlimit",
                defaultvalue,
                required);
        } else {
            this.poolHardlimitOption = setShortLongOpt(
                parser,
                OPT_NAME_POOL_HARDLIMIT_SHORT,
                OPT_NAME_POOL_HARDLIMIT_LONG,
                "true/false",
                "Db pool hardlimit",
                required);
        }
    }

    protected void setDatabaseNameOption(final AdminParser parser, final NeededQuadState required) {
        setDatabaseNameOption(parser, required, "Name of the database");
    }

    protected void setDatabaseNameOption(final AdminParser parser, final NeededQuadState required, String description) {
        this.databaseNameOption = setShortLongOpt(parser, OPT_NAME_DBNAME_SHORT, OPT_NAME_DBNAME_LONG, description, true, required);
    }

    @Override
    protected String getObjectName() {
        return "database";
    }

    protected void parseAndSetMandatoryOptions(final AdminParser parser, final Database db) throws InvalidDataException {
        parseAndSetHostname(parser, db);

        parseAndSetDriver(parser, db);

        parseAndSetDBUsername(parser, db);

        parseAndSetPasswd(parser, db);

        parseAndSetMaxUnits(parser, db);

        parseAndSetPoolHardLimit(parser, db);

        parseAndSetPoolInitial(parser, db);

        parseAndSetPoolmax(parser, db);
    }
}
