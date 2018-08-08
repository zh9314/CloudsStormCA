

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;


import commonTool.CommonTool;
import commonTool.Log4JUtils;
import commonTool.TARGZ;
import lambdaExprs.infrasCode.main.ICYAML;
import lambdaExprs.infrasCode.main.Operation;
import lambdaExprs.infrasCode.main.SEQCode;
import lambdaInfrs.credential.UserCredential;
import lambdaInfrs.database.UserDatabase;
import topology.analysis.TopologyAnalysisMain;
import lambdaExprs.infrasCode.main.Code;


@Path("/ctrl")
public class CtrlAgent {
	
	private String signalFilePath = File.separator + "tmp" + File.separator + "running";
	
	private static final String appGzFileRoot = File.separator + "tmp" + File.separator ;
	
	private static final String appsRoot = CommonTool.formatDirWithSep(System.getProperty("user.dir")) + File.separator + "work" + File.separator;

	private static final String topologyInf = "Infs" +File.separator+ "Topology" +File.separator+ "_top.yml";
	private static final String credInf = "Infs" +File.separator+ "UC" +File.separator+ "cred.yml";
	private static final String dbInf = "Infs" +File.separator+ "UD" +File.separator+ "db.yml";
	private static final String icInf = "App" +File.separator+ "infrasCode.yml";
	private static final String logsInf = "Logs" + File.separator;
	
	/**
	 * Test whether the control agent is alive.
	 * @return
	 */
	@Path("/test")
	@GET
	public Response test() {
		return Response.status(200)
				.entity("Control agent is online!\n")
				.build();
	}
	
	@Path("/execute")
	@GET
	public Response execute(@QueryParam("appid") String appID) {
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
					.entity("'appid' is not valid")
					.build();
		String appGzFilePath = appGzFileRoot + appID + ".tar.gz";
		File appGzFile = new File(appGzFilePath);
		if(!appGzFile.exists())
			return Response.status(551)
					.entity("No corresponding GZ file of AppID: " + appID)
					.build();
		
		
		String rootDir = appsRoot + "AppInfs" + File.separator;
		File rootDirF = new File(rootDir);
		if(!rootDirF.exists())
			rootDirF.mkdirs();
		String appRootDir = rootDir + appID + File.separator;
		
		try {
			TARGZ.decompress(appGzFilePath, new File(appRootDir));
		} catch (IOException e) {
			e.printStackTrace();
			return Response.status(520)
					.entity(e.getMessage())
					.build();
		}
		
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		String ICPath = appRootDir + icInf;
		String logsDir = appRootDir + logsInf;
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology())
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		File logsDirF = new File(logsDir);
		if(!logsDirF.exists())
			logsDirF.mkdir();
		Log4JUtils.setInfoLogFile(logsDir + "CloudsStorm.log");
		
		ICYAML ic = new ICYAML(tam.wholeTopology, userCredential, userDatabase);
		if(!ic.loadInfrasCodes(ICPath, appRootDir))
			return Response.status(520)
					.entity("Some problems with the Infras Code!\n"+ICPath)
					.build();
		
		String icLogPath = logsDir + "InfrasCode.log";
		
		ExeThread exeThread = new ExeThread(ic, icLogPath);
		exeThread.start();
		
		/*String icLogStr = "";
		File icLogFile = new File(icLogPath);
		try {
			icLogStr = FileUtils.readFileToString(icLogFile, "UTF-8");
		} catch (IOException e) {
			e.printStackTrace();
			return Response.status(520)
					.entity("Exception! " + e.getMessage())
					.build();
		}*/
		
		return Response.status(200)
				.entity("Infrastructure code is executing!\nExeID: 0")
				.build();
	}
	
	/**
	 * Check the results of executing the infrastructure code
	 * @return
	 */
	@Path("/check")
	@GET
	public Response check(@QueryParam("appid") String appID, @QueryParam("exeid") String exeID) {
		return Response.status(200)
				.entity("Control agent is online!\n")
				.build();
	}
	
	/*
	 *  Requests of controlling underlying infrastructure
	 *  Example
	 *  <request>
	 *  		<AppID>123456<AppID>
	 *      <ObjectType>SubTopology</ObjectType>
	 *      <Objects>sb1||sb2</Objects>
	 *  </request>
	 *  
	 *  note: level 0 identifies that the file is the main topology file.
	 */
	@Path("/provision")
	@POST
	@Consumes("text/xml")
	public Response provision(String xmlmsg) {
		
		long currentMili = System.currentTimeMillis();
		Document doc = null;
		String icLogStr = null, tmpICLogPath;
		try {
			doc = DocumentHelper.parseText(xmlmsg);
		} catch (DocumentException e) {
			e.printStackTrace();
			return Response.status(400)
					.entity("Execption with the input String! "+e.getMessage())
					.build();
		}
		Element rootElt = doc.getRootElement();
		if(!rootElt.getName().equals("request"))
			return Response.status(400)
					.entity("The root element should be 'request' in input string!")
					.build();
		Element idElt = rootElt.element("AppID");
		if(idElt == null || idElt.getTextTrim().equals(""))
			return Response.status(400)
					.entity("'AppID' is not valid!")
					.build();
		Element obtElt = rootElt.element("ObjectType");
		if(obtElt == null || obtElt.getTextTrim().equals(""))
			return Response.status(400)
					.entity("'ObjectType' is not valid!")
					.build();
		Element obsElt = rootElt.element("Objects");
		if(obsElt == null || obsElt.getTextTrim().equals(""))
			return Response.status(400)
					.entity("'Objects' is not valid!")
					.build();

		String objectType = obtElt.getTextTrim();
		String objects = obsElt.getTextTrim();
		String appID = idElt.getTextTrim();
		
		String appRootDir = appsRoot + appID + File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
        	String sysTmpDir = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"));
        	tmpICLogPath = sysTmpDir + File.separator + "IC_"+currentMili+".log";
        	String tmpCSLog = sysTmpDir + File.separator + "CloudsStorm_"+currentMili+".log";
        	
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		
		Log4JUtils.setInfoLogFile(tmpCSLog);
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology())
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		ICYAML icYAML = new ICYAML(tam.wholeTopology, userCredential, userDatabase);
		icYAML.Mode = "LOCAL";
		icYAML.InfrasCodes = new ArrayList<Code>();
		Operation curOP = new Operation();
		curOP.ObjectType = objectType;
		curOP.Objects = objects;
		curOP.Operation = "provision";
		SEQCode seqCode = new SEQCode();
		seqCode.OpCode = curOP;
		icYAML.InfrasCodes.add(seqCode);
		
        	icYAML.run(tmpICLogPath);
        	
        	File icLogFile = new File(tmpICLogPath);
    		try {
    			icLogStr = FileUtils.readFileToString(icLogFile, "UTF-8");
    		} catch (IOException e) {
    			e.printStackTrace();
    			return Response.status(520)
    					.entity("Exception! " + e.getMessage())
    					.build();
    		}
    		return Response.status(200)
					.entity("ExeID: "+currentMili+"\n" + icLogStr)
					.build();

		
	}
	
	
	/**
	 * Check whether there is a signal file to know whether there is a request running.
	 * @return
	 */
	private boolean checkSignal(){
		
		File signalFile = new File(signalFilePath);
		if(signalFile.exists())
			return true;
		else
			return false;
	}
	
	/**
	 * Set up a signal file to show there is a request running.
	 * @return
	 */
	private boolean setSignal(){

		try {
			FileWriter signalFile = new FileWriter(signalFilePath, false);
			signalFile.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private void rmSignal(){
		File signalFile = new File(signalFilePath);
		FileUtils.deleteQuietly(signalFile);
	}

}
