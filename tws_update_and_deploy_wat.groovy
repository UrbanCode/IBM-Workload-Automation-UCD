/**
 * (c) Copyright IBM Corporation 2016, 2017.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
*/

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import groovy.io.FileType
import org.apache.tools.ant.util.*;

isWindows = System.properties['os.name'].toLowerCase().contains('windows')

final def workDir = new File('.').canonicalFile
final def props = new Properties()
final def inputPropsFile = new File(this.args[0]);
final def outputPropsFile = new File(this.args[1]);


try {
    inputPropsStream = new FileInputStream(inputPropsFile);
    props.load(inputPropsStream);
}
catch (IOException e) {
    throw new RuntimeException(e);
}

properties_file = ""
xml_file = ""
rules_file = "_Rules.rules"

//Get connection parameters
wa_HOST=""
wa_PORT=""
wa_USER=""
wa_PASSWORD=""
wa_PROTOCOL=""

println "PROPS KEYS"
println "${props.keySet()}"

println "PROPS VALUES"
println "${props.values()}"

// Get plugin properties
envProps = props['envProps']?.trim()
def dirOffset = props['dir']
def useSystemEncoding = props['useSystemEncoding']?.toBoolean()
def customEncoding = props['customEncoding']?.trim()
tws_path = props['wa_path']
wa_prefix = props['waPrefix']
replace = props['replace']
hasRules = false
rulesMap=[:]


// Determine which encoding to use, if no encoding is specified we will instead read using I/O Streams
def charset = null
def systemEncoding = System.getenv("DS_SYSTEM_ENCODING")
if (customEncoding) {
    charset = Charset.forName(customEncoding)
}
else if (useSystemEncoding && systemEncoding) {
    charset = Charset.forName(System.getenv("DS_SYSTEM_ENCODING"))
    println "Using Readers/Writers with the following encoding ${charset} to read the properties files."
}
else {
    println "Using Input/Output Streams to read the properties file."
}

if (dirOffset) {
    workDir = new File(workDir, dirOffset).canonicalFile
}

println "Working directory: ${workDir.canonicalPath}"

// Search Property file and Mapping File
new File(workDir.toString()).eachFileMatch(~/.*.properties/) { file ->
	    properties_file = file.getName()
		//println "Found properties file " +properties_file
}
if(!properties_file) {
	fileNotFoundError("properties")
}
appName = properties_file.substring(0, properties_file.indexOf('_Mapping'))
println "Deploying application " +appName
rules_file = appName + rules_file

new File(workDir.toString()).eachFileMatch(~/.*.xml/) { file ->
        xml_file = file.getName()
		//println "Found xml file " +xml_file
}
if(!xml_file) {
	fileNotFoundError("xml")
}

def ant = new AntBuilder()

def scanner = ant.fileScanner {
    fileset(dir:workDir.canonicalPath) {
        properties_file.split('\n').each {
            if (it && it.trim().length() > 0) {
                if (!it.contains("*") && !it.contains("?")) {
                    // Create a new property file if one is specified and it does not yet exist:
                    File propFile = new File(workDir.toString(), it.toString().trim())
                    File parent = propFile.getParentFile()
                    if (parent.exists()) {
                        propFile.createNewFile()
                    } else {
                        throw new FileNotFoundException(String.format("Parent directory does not exist for file: %s", it.toString().trim()));
                    }
                }
                include(name:it.trim())
            }
        }
    }
}

def updateProperties(Properties properties) {
    if(envProps) {
        //this is jeffs magic regex to split on ,'s preceded by even # of \ including 0
        envProps.split("(?<=(^|[^\\\\])(\\\\{2}){0,8}),").each { prop ->
            //split out the name
			def parts = prop.split("(?<=(^|[^\\\\])(\\\\{2}){0,8})=",2);
            def propName = parts[0];
            def propValue = parts.size() == 2 ? parts[1] : "";

			//replace \, with just , and then \\ with \
            propName = propName.replace("\\=", "=").replace("\\,", ",").replace("\\\\", "\\")
            propValue = propValue.replace("\\=", "=").replace("\\,", ",").replace("\\\\", "\\")
			propName = propName.replace(wa_prefix,"")
            //println 'updateProperties: ' + propName + '=' + propValue
			if ( properties[propName]) {
				properties.setProperty(propName, propValue)
				println 'update property: ' + propName + '=' + propValue
			} else {
				if (propName == "HOST") {
					wa_HOST = propValue
				} else if (propName == "PORT") {
					wa_PORT = propValue
				} else if (propName == "USERNAME") {
					wa_USER = propValue
				} else if (propName == "PASSWORD") {
					wa_PASSWORD = propValue
				} else if (propName == "PROTOCOL") {
					wa_PROTOCOL = propValue
				} else if (propName.startsWith("REGEX_")) {
					createRulesMap(propName,propValue)
				} else {
					println propName+" not added"
				}
			}
        }
	}
}

def createRulesMap(propName,propValue) {
	hasRules = true
	propName = propName.replace("REGEX_","")
	if (propName.startsWith("JOBSTREAM_")) {
		ruleEntry(propName,propValue, "JOBSTREAM")
	} else if (propName.startsWith("JOB_")) {
		ruleEntry(propName,propValue, "JOB")
	} else if (propName.startsWith("WORKSTATION_")) {
		ruleEntry(propName,propValue, "WORKSTATION")
	} else if (propName.startsWith("EVENTRULE_")) {
		ruleEntry(propName,propValue, "EVENTRULE")
	} else if (propName.startsWith("PROMPT_")) {
		ruleEntry(propName,propValue, "PROMPT")
	} else if (propName.startsWith("RUNCYCLEGROUP_")) {
		ruleEntry(propName,propValue, "RUNCYCLEGROUP")
	} else if (propName.startsWith("VARIABLEVALUE_")) {
		ruleEntry(propName,propValue, "VARIABLEVALUE")
	} else if (propName.startsWith("VARTABLE_")) {
		ruleEntry(propName,propValue, "VARTABLE")
	} else if (propName.startsWith("RESOURCE_")) {
		ruleEntry(propName,propValue, "RESOURCE")
	} else if (propName.startsWith("CALENDAR_")) {
		ruleEntry(propName,propValue, "CALENDAR")
	} else if (propName.startsWith("REFJOBSTREAM_")) {
		ruleEntry(propName,propValue, "REFJOBSTREAM")
	} else if (propName.startsWith("REFJOB_")) {
		ruleEntry(propName,propValue, "REFJOB")
	}
}

def ruleEntry(propName,propValue, type) {
	propName = propName.replace(type + "_","")
	List listRules = rulesMap[type]
	if (listRules == null || listRules.empty) {
		println "create listRules"
		listRules = new ArrayList();
	}
	listRules.add(propName + "=" + propValue)
	rulesMap.put(type,listRules)
}

def createRulesFile(workDir) {
	def ln = System.getProperty('line.separator')
	if (hasRules) {
		rulesFile = new File(workDir.toString() + File.separator + rules_file)
		rulesFile.write("\$RegEx" + ln)

		List list = rulesMap["JOBSTREAM"]
		if (list !=null) {
			rulesFile.append("[JOBSTREAM]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["JOB"]
		if (list !=null) {
			rulesFile.append("[JOB]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["WORKSTATION"]
		if (list !=null) {
			rulesFile.append("[WORKSTATION]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["EVENTRULE"]
		if (list !=null) {
			rulesFile.append("[EVENTRULE]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["PROMPT"]
		if (list !=null) {
			rulesFile.append("[PROMPT]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["RUNCYCLEGROUP"]
		if (list !=null) {
			rulesFile.append("[RUNCYCLEGROUP]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["VARIABLEVALUE"]
		if (list !=null) {
			rulesFile.append("[VARIABLEVALUE]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["VARTABLE"]
		if (list !=null) {
			rulesFile.append("[VARTABLE]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["RESOURCE"]
		if (list !=null) {
			rulesFile.append("[RESOURCE]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["CALENDAR"]
		if (list !=null) {
			rulesFile.append("[CALENDAR]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["REFJOBSTREAM"]
		if (list !=null) {
			rulesFile.append("[REFJOBSTREAM]"+ln)
			writeList(list, ln)
		}
		list = rulesMap["REFJOB"]
		if (list !=null) {
			rulesFile.append("[REFJOB]"+ln)
			writeList(list, ln)
		}
	}
}

def writeList(list, newLine) {
	for(item in list){
		rulesFile.append(item + newLine)
	}

}

def updateWithStreams(File file, Charset charset) {
	props = new Properties()
    if (charset) {
        props = new LayoutPreservingProperties(charset.toString(), false)
    }
    else {
        props = new LayoutPreservingProperties()
    }

    InputStream inStream = null
    try {
        inStream = new FileInputStream(file);
        props.load(inStream)
    }
    finally {
        inStream?.close()
    }
    updateProperties(props)
    OutputStream outStream = null
    try {
        outStream = new FileOutputStream(file)
        props.store(outStream, "")
    }
    finally {
        outStream?.close()
    }
}

def wappman(workDir) {
    def connection_parameters=""



	if (wa_PROTOCOL) {
		connection_parameters = " -protocol " + wa_PROTOCOL
	}
	if (wa_HOST) {
		connection_parameters = connection_parameters +" -host " + wa_HOST
	}
	if (wa_PORT) {
		connection_parameters = connection_parameters +" -port " + wa_PORT
	}
	if (wa_PASSWORD) {
		connection_parameters = connection_parameters +" -password " + wa_PASSWORD
	}
	if (wa_USER) {
		connection_parameters = connection_parameters +" -username " + wa_USER
	}



	def tws_env_command = isWindows ? "tws_env.cmd" : "tws_env.sh"
    def sout = new StringBuilder(), serr = new StringBuilder()

	def command = isWindows ? tws_path+File.separator+tws_env_command+" && wappman "+ connection_parameters +"-import " + xml_file + " " +properties_file : ". "+tws_path+File.separator+tws_env_command+";wappman -import " + xml_file + " " +properties_file

	if (hasRules) {
		command = command + " -translationRules "+ rules_file
	}

	//println "Run command " + command

	def proc = null
	if (isWindows) {
		proc = ["cmd", "/c", command].execute();
	}  else {
		proc = ["sh", "-c", command].execute();
	}

    proc.consumeProcessOutput(sout, serr)
    proc.waitFor()                               // Wait for the command to finish
	def exitValue = proc.exitValue()
	int count = sout.toString().readLines().size()

	if (serr.indexOf('AWSJCO016E')>0 && replace.toBoolean()) {
		println "The application exists, replacing..."
		def retry_sout = new StringBuilder(), retry_serr = new StringBuilder()
		command = isWindows ? tws_path+File.separator+tws_env_command+" && wappman -replace " + xml_file + " " +properties_file : ". "+tws_path+File.separator+tws_env_command+";wappman -replace " + xml_file + " " +properties_file
		if (connection_parameters) {
			command = command + connection_parameters
		}

		if (hasRules) {
			command = command + " -translationRules "+ rules_file
		}

		if (isWindows) {
			retry_proc = ["cmd", "/c", command].execute();
		}  else {
			retry_proc = ["sh", "-c", command].execute();
		}

		retry_proc.consumeProcessOutput(retry_sout, retry_serr)
		retry_proc.waitFor()                               // Wait for the command to finish
	    exitValue = retry_proc.exitValue()

		println "$retry_sout"
		println "$retry_serr"
		if( exitValue != 0 ) {
			println "Exit value: $exitValue"
			System.exit(1);
		}
	} else {
		println "$sout"
		println "$serr"
	}
	//println "return code: ${ proc.exitValue()}"
	println "Exit value: $exitValue"
    if( exitValue != 0 ) {
        System.exit(1);
    }
}

def fileNotFoundError(file) {
    println "File $file not found"
	System.exit(1);
}

propFile = new File(properties_file);
//println "Property File: ${propFile.canonicalPath}"
println "Additional Update Properties:\n${envProps?:''}\n"

try {
        updateWithStreams(propFile, charset)
		createRulesFile(workDir)
        wappman(workDir)
    }
    catch (Exception e) {
        println "Error: ${e.message}"
        e.printStackTrace()
        System.exit(1)
    }
System.exit(0)
