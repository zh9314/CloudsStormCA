import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import commonTool.CommonTool;


public class ExeOperation {
	
	private static final String signalFileDir = CommonTool.formatDirWithSep(System.getProperty("java.io.tmpdir"));
	
	/**
	 * Check whether there is a signal file to know whether there is a request running.
	 * @return
	 */
	public static boolean checkSignal(String appID){
		String signalFilePath = signalFileDir + appID;
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
	public static boolean setSignal(String appID){
		String signalFilePath = signalFileDir + appID;
		try {
			FileWriter signalFile = new FileWriter(signalFilePath, false);
			signalFile.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static void releaseSignal(String appID){
		String signalFilePath = signalFileDir + appID;
		File signalFile = new File(signalFilePath);
		FileUtils.deleteQuietly(signalFile);
	}

}
