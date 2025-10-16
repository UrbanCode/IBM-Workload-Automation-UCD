/**
 * (c) Copyright IBM Corporation 2016, 2017.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
*/


import com.urbancode.air.plugin.cyberark.NewAirPluginTool

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import groovy.io.FileType
import org.apache.tools.ant.util.*;

isWindows = System.properties['os.name'].toLowerCase().contains('windows')

def apTool = new NewAirPluginTool(this.args[0], this.args[1])
def workDir = new File('.').canonicalFile
def properties = apTool.getStepProperties();
final def props = new Properties()
final def inputPropsFile = new File(this.args[0]);
final def outputPropsFile = new File(this.args[1]);


def JCLCARD =            "//TWSRVOP JOB"
final def comment =      "//*"
def JCLLLIB =            "//MYJCLLIB JCLLIB ORDER=#jcllib#"
final def PIFSTEP =      "//PIFSTEP EXEC EQQYXJPX,"
final def VERSION =      "// VER=V930,"
def SUBSYSTEM =    "// SUBSYS=#subsystem#"
final def OUTDATA =      "//OUTDATA  DD SYSOUT=*,LRECL=4096"
final def OUTBL =        "//OUTBL    DD SYSOUT=*"
final def SYSIN =        "//SYSIN    DD *"
final def OPTIONS1 =     "OPTIONS DBMODE(EXPORT) POSTPROC(Y)"
final def LOADDEF =      "LOADDEF * DATA(-) LOADER(*)"
final def DD =           "//         DD *"
final def OPTIONS2 =     "OPTIONS DBMODE(REPLACE)"
final def end =          "/*"



try {
    inputPropsStream = new FileInputStream(inputPropsFile);
    props.load(inputPropsStream);
}
catch (IOException e) {
    throw new RuntimeException(e);
}

txt_file = ""

println "PROPS KEYS"
println "${props.keySet()}"

println "PROPS VALUES"
println "${props.values()}"

final def APPL_ID="FEKAPPL";


// Get plugin properties
envProps = props['envProps']?.trim()
def dirOffset = props['dir']
def useSystemEncoding = props['useSystemEncoding']?.toBoolean()
def customEncoding = props['customEncoding']?.trim()
_subsystem = props['subsystem']
_job_card = props['job_card']
_jcllib = props['jcllib']
wa_prefix = props['waPrefix']

// Replace variables into JCL
if (_job_card)
{
	JCLCARD = JCLCARD + " " + _job_card
}
JCLLLIB = JCLLLIB.replaceAll("#jcllib#", _jcllib )
SUBSYSTEM = SUBSYSTEM.replaceAll("#subsystem#", _subsystem )

jclString = JCLCARD + "\n" + comment + "\n"+  JCLLLIB + "\n" + PIFSTEP + "\n" + VERSION + "\n" + SUBSYSTEM + "\n" + OUTDATA + "\n" + OUTBL + "\n" + SYSIN + "\n" + OPTIONS1 + "\n" + LOADDEF + "\n"

// Determine which encoding to use, if no encoding is specified we will instead read using I/O Streams
def charset = null
def systemEncoding = System.getenv("DS_SYSTEM_ENCODING")
if (customEncoding) {
    charset = customEncoding
}
else if (useSystemEncoding && systemEncoding) {
    charset = System.getenv("DS_SYSTEM_ENCODING")
    println "Using Readers/Writers with the following encoding ${charset} to read the properties files."
}
else {
    println "Using Input/Output Streams to read the properties file."
}

if (dirOffset) {
    workDir = new File(workDir, dirOffset).canonicalFile
}

println "Working directory: ${workDir.canonicalPath}"

// Search txt File
new File(workDir.toString()).eachFileMatch(~/.*.txt/) { file ->
	    txt_file = file.getName()
		println "Found txt file " +txt_file
}
if(!txt_file) {
	fileNotFoundError("txt")
}

//appName = txt_file.substring(0, txt_file.indexOf('_Mapping'))
//println "Deploying application " +appName


def ant = new AntBuilder()

def translate() {
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
	        ["WS_", "AD_", "SR_", "CL_", "OW_", "JS_", "PR_"].each { prefix ->
				if (propName.startsWith(prefix)) {
					objType = prefix.replace("_", "")
					propName = propName.replace(prefix, "")
					jclString = jclString + "\n" + "TRANSLATE "+ objType +" OLD("+ propName + ") NEW("+ propValue +")\n"
				}
			}
	    }
    }
}

def  translateWS(propName, propValue) {
	jclString = jclString.replaceAll("WSID\\("+propName+"\\)", "WSID("+propValue+")")
	jclString = jclString.replaceAll("WSNAME\\("+propName+"\\)", "WSNAME("+propValue+")")
	println "translated " +jclString
}

def fileNotFoundError(file) {
    println "File $file not found"
	System.exit(1);
}

//Add TRANSLATE statement
translate()

// Add Application definition
propFile = new File(txt_file);
if (charset) {
	new File(workDir.toString() + File.separator + txt_file).eachLine(charset) { line ->
		jclString = jclString + line + "\n"
	}
} else {
	new File(workDir.toString() + File.separator + txt_file).eachLine() { line ->
		jclString = jclString + line + "\n"
	}
}

jclString = jclString + "\n"+ OPTIONS2 + "\n" + end
println "=============================================="
println "JCL String"
println "=============================================="
println jclString

//println "Environment Properties:\n${envProps?:''}\n"

apTool.setOutputProperty("jclString", "${jclString}");
apTool.setOutputProperty("jobCard", "${JCLCARD}");
apTool.storeOutputProperties();


System.exit(0)
