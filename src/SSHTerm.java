

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.apache.commons.io.FileUtils;

import commonTool.CommonTool;
import topology.analysis.TopologyAnalysisMain;
import topology.description.actual.VM;


@ServerEndpoint(value = "/sshterm/{vmName}/{appID}")
public class SSHTerm {
	private static final String appsRoot = CommonTool.formatDirWithSep(System.getProperty("user.dir")) + "work" + File.separator;
	//private static final String appsRoot = "/Users/zh9314/work/";
	
	private static final String topologyInf = "Infs" +File.separator+ "Topology" +File.separator+ "_top.yml";
		
		
	private Session curSession = null;
	private Process curShell = null;
	private BufferedReader stdInput = null;
	private int connected = 0;   //// whether the term is connected. 0: have not tried; 1: connecting; -1: cannot connect; 2: connected
	private boolean started = false;
	
	private String prompt = null;
	private String keyPath = null;

	private String endTag = "2c00aa94-d5fe-4ebe-a8fb-d0a5a007ce1f"+"75cd2504-8f67-4ffe-a039-45508aa8c10c";
	private boolean endStdOutput = false, endErrOutput = false;
	
	@OnOpen
	public void onOpen(Session session, @PathParam("vmName") String vmName, @PathParam("appID") String appID){
		System.out.println("Success");
		curSession = session;
		startBash(vmName, appID);
	}
	
	@OnMessage
    public void onMessage(Session session, byte[] message, @PathParam("vmName") String vmName, @PathParam("appID") String appID) {
		System.out.println("GET A BMSG! "+message.length+" "+ message[0]+" "+ message[1]);
		
		if(message[1] == 3 || message[1] == 4){
			if(curShell != null){
				curShell.destroyForcibly();
				curShell = null;
			}
			curSession = session;
			try {
				started = false;
				connected = 0;
				curSession.getBasicRemote().sendText(endTag);
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H");
			} catch (IOException e) {
				e.printStackTrace();
			}
			startBash(vmName, appID);
		}
	}
	
	@OnMessage
    public void onMessage(Session session, String message) {
		System.out.println("GET A MSG! "+message);
		if(!started)
			return ;
		try {
			if(message.trim().equals("clear")){
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H"+prompt);
				return;
			} 
			
			if(message.trim().equals("exit")){
				curSession.getBasicRemote().sendText("\r\n"+prompt);
				curSession.close();
				return;
			}
		
			Writer strWriter = new OutputStreamWriter(curShell.getOutputStream());
		
			endStdOutput = false;
			endErrOutput = false;
			strWriter.write(message+"\n");
			strWriter.flush();
			
			strWriter.write("echo '"+endTag+"'; "+endTag+"\n");
			strWriter.flush();
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@OnClose
	public void onClose() {
		if(curShell != null){
			curShell.destroyForcibly();
			curShell = null;
		}
	}
	
	
	private void startBash(String vmName, String appID){
		System.out.println(vmName+" "+appID);
		
		if(appID == null || appID.trim().equals(""))
			return ;
		String topTopologyLoadingPath = appsRoot + "AppInfs" + File.separator + appID + File.separator + topologyInf;
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
					+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return ;
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if( !tam.fullLoadWholeTopology() )
			return ;
		
		VM curVM = tam.wholeTopology.VMIndex.get(vmName);
		if(curVM == null)
			return ;
		if(curVM.publicAddress == null || curVM.publicAddress.equals(""))
			return ;
		
		prompt = "\033[1;3;31mroot@"+vmName+"\033[1;3;33m("+curVM.publicAddress+")\033[0m # ";
		String promptPure = "root@"+vmName+"("+curVM.publicAddress+") # ";
		
		keyPath = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"))
				+ File.separator + "ssh_tmp_"+System.currentTimeMillis()+".key";
		FileWriter keyFw;
		try {
			keyFw = new FileWriter(keyPath, false);
			keyFw.write(curVM.ponintBack2STI.subTopology.accessKeyPair.privateKeyString);
			keyFw.close();
			
			Process p = Runtime.getRuntime().exec("chmod 400 "+keyPath);
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		String defaultSSHAccount = curVM.defaultSSHAccount;
		ProcessBuilder pb = new ProcessBuilder("ssh", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", 
												"-i", keyPath,defaultSSHAccount+"@"+curVM.publicAddress);

		try {
			pb.redirectErrorStream(true);
			welcomePage();
			curShell = pb.start();
			stdInput = new BufferedReader(new 
				     InputStreamReader(curShell.getInputStream()));
			new Thread(new Runnable(){
			    public void run(){
			    		try {
			    			String returnString = "";
			    			connected = 0;
			    	    		while ((returnString = stdInput.readLine()) != null) {
			    	    			System.out.println(returnString);
			    	    			if(!started){
			    	    				if(returnString.contains("Pseudo-terminal")){
			    	    					connected = 1;
			    	    					continue;
			    	    				}
			    	    				
			    	    				if(connected == 1){
			    	    					if(returnString.contains("Connection refused")){
				    	    					connected = -1;
				    	    					curSession.getBasicRemote().sendText("\033[u\033[K\033[1;31mFailed!\033[0m\r\n");
			    	    					}
				    	    				else{
				    	    					curSession.getBasicRemote().sendText("\033[u\033[K\033[1;32mConnected!\033[0m\r\n");
				    	    					connected = 2;
				    	    				}
			    	    				}
			    	    				
			    	    				if(connected == 2){
			    	    					if(returnString.contains("stdin: is not a tty")){
			    	    						curSession.getBasicRemote().sendText("prompt::::"+prompt);
				    	    					curSession.getBasicRemote().sendText(prompt);
				    	    					curSession.getBasicRemote().sendText("promptPure::::"+promptPure);
				    	    					started = true;
				    	    				}
			    	    					else
			    	    						curSession.getBasicRemote().sendText(returnString+"\r\n");
			    	    				}
			    	    				
			    	    			}else{
			    	    				if(returnString.equals(endTag))
				    	    				endStdOutput = true;
			    	    				else if(returnString.contains(endTag) && returnString.contains("not found"))
			    	    					endErrOutput = true;
				    	    			else if(returnString.equals("echo '"+endTag+"'; "+endTag) )
				    	    				;
				    	    			else
				    	    				curSession.getBasicRemote().sendText(returnString+"\r\n");
			    	    				
			    	    				if(endStdOutput && endErrOutput)
			    	    					curSession.getBasicRemote().sendText(prompt);
			    	    			}
			    	    		}
			    	    		FileUtils.deleteQuietly(new File(keyPath));
			    	    		System.out.println("Terminated");
				    	    		
					} catch (IOException e) {
						e.printStackTrace();
					}
			    }
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void welcomePage(){
		try {
			String CloudsStormText = "  _______             __    ______               \r\n";
			      CloudsStormText += " / ___/ /__  __ _____/ /__ / __/ /____  ______ _ \r\n";
			      CloudsStormText += "/ /__/ / _ \\/ // / _  (_-<_\\ \\/ __/ _ \\/ __/  ' \\\r\n";
			      CloudsStormText += "\\___/_/\\___/\\_,_/\\_,_/___/___/\\__/\\___/_/ /_/_/_/\r\n";
			curSession.getBasicRemote().sendText("Welcome to\r\n");
			curSession.getBasicRemote().sendText(CloudsStormText);
			curSession.getBasicRemote().sendText("\033[s\033[1;31mConnecting...\033[0m  ");
			new Thread(new Runnable(){
				 public void run(){
					 try {
		    				int i = 0;
		    				while(connected == 1 || connected == 0){
		    					String symbol = null;
		    					if(i%4 == 0)
		    						symbol = "-";
		    					else if(i%4 == 1)
		    						symbol = "\\";
		    					else if(i%4 == 2)
		    						symbol = "|";
		    					else
		    						symbol = "/";
		    					curSession.getBasicRemote().sendText("\033[1D\033[K\033[1;31m"+symbol+"\033[0m");
			    	    			Thread.sleep(100);
			    	    			i++;
		    				}
		    	    		
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					}
				 }
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

}