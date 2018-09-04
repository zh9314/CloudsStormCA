

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
	private boolean thereExistOutput = false;
	private boolean started = false;
	private boolean enterBlocked = false;   /// block the enter key
	private boolean connected = false;   //// whether the term is connected
	
	private String prompt = null;
	private String keyPath = null;
	
	@OnOpen
	public void onOpen(Session session, @PathParam("vmName") String vmName, @PathParam("appID") String appID){
		System.out.println("Success");
		curSession = session;
		startBash(vmName, appID);
	}
	
	@OnMessage
    public void onMessage(Session session, byte[] message, @PathParam("vmName") String vmName, @PathParam("appID") String appID) {
		System.out.println("GET A BMSG! "+message.length+" "+ message[0]+" "+ message[1]);
		if(message[0] == 13){
			ReadChecker rc = new ReadChecker();
			Thread check = new Thread(rc);
			check.start();
			try {
				Thread.sleep(400);
				if(!enterBlocked && !thereExistOutput){
					Thread.sleep(100);
					if(!rc.sth && curShell != null)
						session.getBasicRemote().sendText("\r\n"+prompt);
				}
				enterBlocked = false;
				
			} catch ( IOException | InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
		if(message[1] == 3 || message[1] == 4){
			if(curShell != null){
				curShell.destroyForcibly();
				curShell = null;
			}
			curSession = session;
			try {
				started = false;
				enterBlocked = true;
				connected = false;
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H");
			} catch (IOException e) {
				e.printStackTrace();
			}
			startBash(vmName, appID);
		}
	}
	
	@OnMessage
    public void onMessage(Session session, String message) {
		thereExistOutput = false;
		if(message.trim().equals("")){
			enterBlocked = true;
			return ;
		}
		System.out.println("GET A MSG! "+message);
		try {
			if(message.trim().equals("clear")){
				curSession.getBasicRemote().sendText("\033[2J\033[0;0H"+prompt);
				enterBlocked = true;
				return;
			} 
			
			if(message.trim().equals("exit")){
				curSession.getBasicRemote().sendText("\r\n"+prompt);
				curSession.close();
				return;
			}
		
			Writer strWriter = new OutputStreamWriter(curShell.getOutputStream());
		
			strWriter.write(message+"\n");
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
			thereExistOutput = false;
			curSession.getBasicRemote().sendText("prompt::::"+prompt);
			welcomePage();
			curShell = pb.start();
			stdInput = new BufferedReader(new 
				     InputStreamReader(curShell.getInputStream()));
			new Thread(new Runnable(){
			    public void run(){
			    		try {
			    			String returnString = "";
			    			connected = false;
			    	    		while ((returnString = stdInput.readLine()) != null) {
			    	    			System.out.println(returnString);
			    	    			if(!started){
			    	    				if(returnString.contains("Pseudo-terminal")){
			    	    					continue;
			    	    				}
			    	    				if(returnString.contains("stdin: is not a tty")){
			    	    					curSession.getBasicRemote().sendText("\r\n"+prompt);
			    	    					started = true;
			    	    				}
			    	    				else{
			    	    					if(!connected){
			    	    						curSession.getBasicRemote().sendText("\033[u\033[K\033[1;32mConnected!\033[0m\r\n");
			    	    						connected = true;
			    	    					}
			    	    					curSession.getBasicRemote().sendText(returnString+"\r\n");
			    	    					continue;
			    	    				}
			    	    			}else{
			    	    				curSession.getBasicRemote().sendText("\r\n"+returnString);
				    	    			thereExistOutput = true;
				    	    			if(!stdInput.ready())
				    	    				curSession.getBasicRemote().sendText("\r\n"+prompt);
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
		    				while(!connected){
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
	
	class ReadChecker implements Runnable {
		public boolean sth = false;
		
		@Override
	    public void run(){
    			try {
    				int count = 10, i = 0;
    				while(i<count){
	    	    			if(stdInput.ready()){
	    	    				sth = true;
	    	    			}
	    	    			Thread.sleep(100);
	    	    			i++;
    				}
    	    		
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
	    }
	}
	

}