

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import commonTool.CommonTool;
import commonTool.Log4JUtils;
import commonTool.TARGZ;
import lambdaExprs.infrasCode.main.ICYAML;
import lambdaExprs.infrasCode.main.Operation;
import lambdaExprs.infrasCode.main.SEQCode;
import lambdaInfrs.credential.UserCredential;
import lambdaInfrs.database.UserDatabase;
import lambdaInfrs.engine.TEngine.TEngine;
import lambdaInfrs.request.DeleteRequest;
import topology.analysis.TopologyAnalysisMain;
import lambdaExprs.infrasCode.log.Logs;
import lambdaExprs.infrasCode.main.Code;


@Path("/ctrl")
public class CtrlAgent {
	
	
	private static final String appGzFileRoot = File.separator + "tmp" + File.separator ;
	
	//private static final String appsRoot = CommonTool.formatDirWithSep(System.getProperty("user.dir")) + "work" + File.separator;
	private static final String appsRoot = "/Users/zh9314/work/";
	
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
		
		/*if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();*/
		
		String appGzFilePath = appGzFileRoot + appID + ".tar.gz";
		File appGzFile = new File(appGzFilePath);
		if(!appGzFile.exists()){
			ExeOperation.releaseSignal(appID);
			return Response.status(551)
					.entity("No corresponding GZ file of AppID: " + appID)
					.build();
		}
		
		String rootDir = appsRoot + "AppInfs" + File.separator;
		File rootDirF = new File(rootDir);
		if(!rootDirF.exists())
			rootDirF.mkdirs();
		String appRootDir = rootDir + appID + File.separator;
		
		try {
			TARGZ.decompress(appGzFilePath, new File(appRootDir));
		} catch (IOException e) {
			e.printStackTrace();
			ExeOperation.releaseSignal(appID);
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
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		File logsDirF = new File(logsDir);
		if(!logsDirF.exists())
			logsDirF.mkdir();
		Log4JUtils.setInfoLogFile(logsDir + "CloudsStorm.log");
		
		ICYAML ic = new ICYAML(tam.wholeTopology, userCredential, userDatabase);
		if(!ic.loadInfrasCodes(ICPath, appRootDir)){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with the Infras Code!\n"+ICPath)
					.build();
		}
		ic.curDir = System.getProperty("java.io.tmpdir");
		ic.rootDir = System.getProperty("java.io.tmpdir");
		
		String icLogPath = logsDir + "InfrasCode.log";
		
		ExeThread exeThread = new ExeThread(ic, icLogPath, appID);
		exeThread.start();
		
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
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
						.entity("'appid' is not valid")
						.build();
		if(exeID == null || exeID.trim().equals(""))
			return Response.status(400)
						.entity("'exeid' is not valid")
						.build();
		
		String icLogPath = null;
		if(exeID.trim().equals("0"))
			icLogPath = appsRoot + "AppInfs" + File.separator + appID 
								 + File.separator + logsInf + "InfrasCode.log";
		else
			icLogPath = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"))
								+ File.separator + "IC_"+appID+"_"+exeID+".log";
		
		File icLogFile = new File(icLogPath);
		if(!icLogFile.exists())
			return Response.status(420)
					.entity("Cannot find the corresponding log file of AppID="+appID+" ExeID="+exeID+"!")
					.build();
		
		try {
			String icLogStr = FileUtils.readFileToString(icLogFile, "UTF-8");
			if(icLogStr.length() < 10)
				return Response.status(553)
						.entity("Execution has not completed yet!")
						.build();

			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			Logs logContent = mapper.readValue(new File(icLogPath), Logs.class);
			for(int li = logContent.LOGs.size() - 1 ; li >= 0  ; li--){
				Map<String, String> logTerm = logContent.LOGs.get(li).LOG;
				if(logTerm != null){
					for(Map.Entry<String, String> entry : logTerm.entrySet()){
						String logKey = entry.getKey();
						if(logKey.toLowerCase().contains("error"))
							return Response.status(520)
									.entity("It seems there are problems during execution!")
									.build();
					}
				}
			}
			
			return Response.status(200)
					.entity("Execution is successful!")
					.build();
			
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(520)
					.entity("Unknown: "+e.getMessage())
					.build();
		}
        
	}
	
	/**
	 * Check the logs of the infrastructure code
	 * @return
	 */
	@Path("/check/iclog")
	@GET
	public Response checkICLog(@QueryParam("appid") String appID, @QueryParam("exeid") String exeID) {	
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
						.entity("'appid' is not valid")
						.build();
		if(exeID == null || exeID.trim().equals(""))
			return Response.status(400)
						.entity("'exeid' is not valid")
						.build();
		
		String icLogPath = null;
		if(exeID.trim().equals("0"))
			icLogPath = appsRoot + "AppInfs" + File.separator + appID 
								 + File.separator + logsInf + "InfrasCode.log";
		else
			icLogPath = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"))
								+ File.separator + "IC_"+appID+"_"+exeID+".log";
		
		File icLogFile = new File(icLogPath);
		if(!icLogFile.exists())
			return Response.status(420)
					.entity("Cannot find the corresponding log file of AppID="+appID+" ExeID="+exeID+"!")
					.build();
		
		try {
			String icLogStr = FileUtils.readFileToString(icLogFile, "UTF-8");
			if(icLogStr.length() < 10)
				return Response.status(553)
						.entity("Execution has not completed yet!")
						.build();
    		
			return Response.status(200)
					.entity(icLogStr)
					.build();
			
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(520)
					.entity(e.getMessage())
					.build();
		}
        
	}
	
	/**
	 * Check the logs of the CloudsStorm
	 * @return
	 */
	@Path("/check/cslog")
	@GET
	public Response checkCSLog(@QueryParam("appid") String appID, @QueryParam("exeid") String exeID) {	
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
						.entity("'appid' is not valid")
						.build();
		if(exeID == null || exeID.trim().equals(""))
			return Response.status(400)
						.entity("'exeid' is not valid")
						.build();
		
		String icLogPath = null;
		if(exeID.trim().equals("0"))
			icLogPath = appsRoot + "AppInfs" + File.separator + appID 
								 + File.separator + logsInf + "CloudsStorm.log";
		else
			icLogPath = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"))
								+ File.separator + "CloudsStorm_"+appID+"_"+exeID+".log";
		
		File icLogFile = new File(icLogPath);
		if(!icLogFile.exists())
			return Response.status(420)
					.entity("Cannot find the corresponding log file of AppID="+appID+" ExeID="+exeID+"!")
					.build();
		
		try {
			String icLogStr = FileUtils.readFileToString(icLogFile, "UTF-8");
			if(icLogStr.length() < 10)
				return Response.status(553)
						.entity("Execution has not completed yet!")
						.build();
    		
			return Response.status(200)
					.entity(icLogStr)
					.build();
			
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(520)
					.entity(e.getMessage())
					.build();
		}
        
	}
	
	@Path("/delete/ctrl")
	@GET
	public Response deleteCtrl(@QueryParam("appid") String appID) {
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
						.entity("'appid' is not valid")
						.build();
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				 									+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();
		
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		String logsDir = appRootDir + logsInf;
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		File logsDirF = new File(logsDir);
		if(!logsDirF.exists())
			logsDirF.mkdir();
		Log4JUtils.setInfoLogFile(logsDir + "CloudsStorm.log");
		
		TEngine tEngine = new TEngine();
		tEngine.deleteAll(tam.wholeTopology, userCredential, userDatabase);
		
		Log4JUtils.removeAllLogAppender();
		
		ExeOperation.releaseSignal(appID);
		return Response.status(200)
				.entity("All resources (including CA) are deleted!")
				.build();
		
	}
	
	@Path("/delete/all")
	@GET
	public Response deleteAll(@QueryParam("appid") String appID) {
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
						.entity("'appid' is not valid")
						.build();
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				 									+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();
		
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		String logsDir = appRootDir + logsInf;
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		File logsDirF = new File(logsDir);
		if(!logsDirF.exists())
			logsDirF.mkdir();
		Log4JUtils.setInfoLogFile(logsDir + "CloudsStorm.log");
		
		TEngine tEngine = new TEngine();
		DeleteRequest deleteReq = new DeleteRequest();
		for(int si = 0 ; si<tam.wholeTopology.topologies.size() ; si++){
			if(tam.wholeTopology.topologies.get(si).topology.equalsIgnoreCase("_ctrl"))
				continue;
			deleteReq.content.put(tam.wholeTopology.topologies.get(si).topology, false);
		}
		tEngine.delete(tam.wholeTopology, userCredential, userDatabase, deleteReq);
		
		Log4JUtils.removeAllLogAppender();
		
		ExeOperation.releaseSignal(appID);
		return Response.status(200)
				.entity("All resources (excluding CA) are deleted!")
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
		return exeICCMD(xmlmsg, "provision");

	}
	

	/*
	 *  Requests of controlling underlying infrastructure
	 *  Example
	 *  <request>
	 *  		<AppID>123456<AppID>
	 *      <ObjectType>SubTopology</ObjectType>
	 *      <Objects>sb1||sb2</Objects>
	 *      <Log>false</Log>
	 *      <CMDs><CMD0>echo test</CMD0><CMD1>echo ok</CMD1></CMDs>
	 *  </request>
	 *  
	 *  note: level 0 identifies that the file is the main topology file.
	 */
	@Path("/execute/cmd")
	@POST
	@Consumes("text/xml")
	public Response executeCMDs(String xmlmsg){
		long currentMili = System.currentTimeMillis();
		Document doc = null;
		boolean logOn = true;
		ArrayList<String> cmds = new ArrayList<String>();
		String tmpICLogPath;
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
		Element logElt = rootElt.element("Log");
		if(logElt != null && logElt.getTextTrim().equalsIgnoreCase("false"))
			logOn = false;
		Element cmdsElt = rootElt.element("CMDs");
		if(cmdsElt == null)
			return Response.status(400)
					.entity("'CMDs' is not valid!")
					.build();
		int cmdIndex = 0;
		while(true){
			Element cmdElt = cmdsElt.element("CMD"+cmdIndex);
			if(cmdElt == null)
				break;
			cmds.add(cmdElt.getTextTrim());
			cmdIndex++;
		}
		if(cmds.size() == 0)
			return Response.status(400)
					.entity("No valid command in the request!")
					.build();

		String objectType = obtElt.getTextTrim();
		String objects = obsElt.getTextTrim();
		String appID = idElt.getTextTrim();
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				 								+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();
		
        	String sysTmpDir = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"));
        	tmpICLogPath = sysTmpDir + "IC_"+appID+"_"+currentMili+".log";
        	String tmpCSLog = sysTmpDir + "CloudsStorm_"+appID+"_"+currentMili+".log";
        	
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		
		Log4JUtils.setInfoLogFile(tmpCSLog);
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		ICYAML icYAML = new ICYAML(tam.wholeTopology, userCredential, userDatabase);
		icYAML.Mode = "LOCAL";
		icYAML.InfrasCodes = new ArrayList<Code>();
		for(int ci = 0 ; ci<cmds.size() ; ci++){
			Operation curOP = new Operation();
			curOP.ObjectType = objectType;
			curOP.Objects = objects;
			if(!logOn)
				curOP.Log = "off";
			curOP.Operation = "execute";
			curOP.Command = cmds.get(ci);
			SEQCode seqCode = new SEQCode();
			seqCode.OpCode = curOP;
			icYAML.InfrasCodes.add(seqCode);
		}
		
		icYAML.curDir = System.getProperty("java.io.tmpdir");
		icYAML.rootDir = System.getProperty("java.io.tmpdir");
		
		ExeThread exeThread = new ExeThread(icYAML, tmpICLogPath, appID);
		exeThread.start();
		
    		return Response.status(200)
					.entity(String.valueOf(currentMili))
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
	@Path("/delete")
	@POST
	@Consumes("text/xml")
	public Response delete(String xmlmsg) {
		return exeICCMD(xmlmsg, "delete");

	}
	
	private Response exeICCMD(String xmlmsg, String operation){
		long currentMili = System.currentTimeMillis();
		Document doc = null;
		String tmpICLogPath;
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
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				 								+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();
		
        	String sysTmpDir = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"));
        	tmpICLogPath = sysTmpDir + "IC_"+appID+"_"+currentMili+".log";
        	String tmpCSLog = sysTmpDir + "CloudsStorm_"+appID+"_"+currentMili+".log";
        	
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		
		Log4JUtils.setInfoLogFile(tmpCSLog);
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
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
		curOP.Operation = operation;
		SEQCode seqCode = new SEQCode();
		seqCode.OpCode = curOP;
		icYAML.InfrasCodes.add(seqCode);
		

		icYAML.curDir = System.getProperty("java.io.tmpdir");
		icYAML.rootDir = System.getProperty("java.io.tmpdir");
		
		ExeThread exeThread = new ExeThread(icYAML, tmpICLogPath, appID);
		exeThread.start();
		
    		return Response.status(200)
					.entity(String.valueOf(currentMili))
					.build();
	}
	
	
	/*
	 *  Requests of controlling underlying infrastructure
	 *  Example
	 *  <request>
	 *  		<AppID>123456</AppID>
	 *      <ScaleReq>
	 *      <ScaleReq0>
	 *      		<ObjectType>SubTopology</ObjectType>
	 *          <Objects>sb1||sb2</Objects>
	 *      		<CP>ExoGENI</CP>
	 * 	        <DC>GWU (Washington DC,  USA) XO Rack</DC>
	 *          <ScaledSTName>scaling1</ScaledSTName>
	 *	        <OutIn>In</OutIn>
	 *		</ScaleReq0>
	 *      <ScaleReq1>
	 *      		<ObjectType>VM</ObjectType>
	 *          <Objects>sb1.vm2||sb2.vm3</Objects>
	 *      		<CP>ExoGENI</CP>
	 * 	        <DC>GWU (Washington DC,  USA) XO Rack</DC>
	 *          <ScaledSTName>scaling2</ScaledSTName>
	 *	        <OutIn>Out</OutIn>
	 *		</ScaleReq1>
	 *      </ScaleReq>
	 *  </request>
	 *  
	 */
	@Path("/hscale")
	@POST
	@Consumes("text/xml")
	public Response hscale(String xmlmsg) {
		long currentMili = System.currentTimeMillis();
		Document doc = null;
		String tmpICLogPath;
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
		Element reqsElt = rootElt.element("ScaleReq");
		if(reqsElt == null)
			return Response.status(400)
					.entity("'ScaleReq' is not valid!")
					.build();
		ArrayList<HScalingRequest> scalingReqs = new ArrayList<HScalingRequest>();
		int reqIndex = 0;
		while(true){
			Element reqElt = reqsElt.element("ScaleReq"+reqIndex);
			if(reqElt == null)
				break;
			HScalingRequest req = new HScalingRequest();
			req.reqID = "req"+reqIndex;
			Element obtElt = reqElt.element("ObjectType");
			if(obtElt == null || obtElt.getTextTrim().equals(""))
				return Response.status(400)
						.entity("'ObjectType' is not valid!")
						.build();
			Element obsElt = reqElt.element("Objects");
			if(obsElt == null || obsElt.getTextTrim().equals(""))
				return Response.status(400)
						.entity("'Objects' is not valid!")
						.build();
			Element cpElt = reqElt.element("CP");
			if(cpElt == null || cpElt.getTextTrim().equals(""))
				req.cloudProvider = null;
			else
				req.cloudProvider = cpElt.getTextTrim();
			
			Element dcElt = reqElt.element("DC");
			if(dcElt == null || dcElt.getTextTrim().equals(""))
				req.dataCentre = null;
			else
				req.dataCentre = dcElt.getTextTrim();
			
			Element nameElt = reqElt.element("ScaledSTName");
			if(nameElt == null || nameElt.getTextTrim().equals(""))
				req.scaledSTName = null;
			else
				req.scaledSTName = nameElt.getTextTrim();
			
			Element outInElt = reqElt.element("OutIn");
			if(outInElt == null || outInElt.getTextTrim().equals(""))
				req.scalingDirection = null;
			else
				req.scalingDirection = outInElt.getTextTrim();
			
			req.targetObjectType = obtElt.getTextTrim();
			req.targetObjects = obsElt.getTextTrim();
			scalingReqs.add(req);
			reqIndex++;
		}
		if(scalingReqs.size() == 0)
			return Response.status(400)
					.entity("No valid scaling request in the request!")
					.build();
		
		String appID = idElt.getTextTrim();
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				 								+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		if(ExeOperation.checkSignal(appID))
			return Response.status(552)
					.entity("Application of '"+appID+"' is running! Reject request!")
					.build();
		
		if(!ExeOperation.setSignal(appID))
			return Response.status(520)
					.entity("Signal of '"+appID+"' cannot be set!")
					.build();
		
        	String sysTmpDir = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"));
        	tmpICLogPath = sysTmpDir + "IC_"+appID+"_"+currentMili+".log";
        	String tmpCSLog = sysTmpDir + "CloudsStorm_"+appID+"_"+currentMili+".log";
        	
		String topTopologyLoadingPath = appRootDir + topologyInf;
		String credentialsPath = appRootDir + credInf;
		String dbsPath = appRootDir + dbInf;
		
		Log4JUtils.setInfoLogFile(tmpCSLog);
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if(!tam.fullLoadWholeTopology()){
			ExeOperation.releaseSignal(appID);
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		}
		
		UserCredential userCredential = new UserCredential();
		userCredential.loadCloudAccessCreds(credentialsPath);
		UserDatabase userDatabase = new UserDatabase();
		userDatabase.loadCloudDBs(dbsPath);
		
		ICYAML icYAML = new ICYAML(tam.wholeTopology, userCredential, userDatabase);
		icYAML.Mode = "LOCAL";
		icYAML.InfrasCodes = new ArrayList<Code>();
		
		String reqStr = "";
		for(int ri = 0 ; ri<scalingReqs.size() ; ri++){
			HScalingRequest curReq = scalingReqs.get(ri);
			Operation curOP = new Operation();
			curOP.Operation = "hscale";
			curOP.ObjectType = curReq.targetObjectType;
			curOP.Objects = curReq.targetObjects;
			curOP.Options = new HashMap<String, String>();
			curOP.Options.put("ReqID", "req"+ri);
			curOP.Options.put("CP", curReq.cloudProvider);
			curOP.Options.put("DC", curReq.dataCentre);
			curOP.Options.put("OutIn", curReq.scalingDirection);
			curOP.Options.put("ScaledSTName", curReq.scaledSTName);
			if(ri == 0)
				reqStr = "req0";
			else
				reqStr += ("||req"+ri);
			
			SEQCode seqCode = new SEQCode();
			seqCode.OpCode = curOP;
			icYAML.InfrasCodes.add(seqCode);
		}
		
		SEQCode seqCode = new SEQCode();
		Operation curOP = new Operation();
		curOP.Operation = "hscale";
		curOP.ObjectType = "REQ";
		curOP.Objects = reqStr;
		seqCode.OpCode = curOP;
		icYAML.InfrasCodes.add(seqCode);
		
		icYAML.curDir = System.getProperty("java.io.tmpdir");
		icYAML.rootDir = System.getProperty("java.io.tmpdir");
		
		ExeThread exeThread = new ExeThread(icYAML, tmpICLogPath, appID);
		exeThread.start();
		
    		return Response.status(200)
					.entity(String.valueOf(currentMili))
					.build();

	}
	
	
	
	

}
