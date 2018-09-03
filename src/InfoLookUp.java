import java.io.File;
import java.util.ArrayList;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import topology.analysis.TopologyAnalysisMain;
import topology.dataStructure.Member;
import topology.dataStructure.Subnet;
import topology.description.actual.SubTopologyInfo;
import topology.description.actual.VM;

@Path("/lookup")
public class InfoLookUp {
	
	//private static final String appsRoot = CommonTool.formatDirWithSep(System.getProperty("user.dir")) + "work" + File.separator;
	private static final String appsRoot = "/Users/zh9314/work/";
	
	private static final String topologyInf = "Infs" +File.separator+ "Topology" +File.separator+ "_top.yml";

	/**
	 * For the purpose of visualizing all the infrastructure topology, the return value is a JSON String.
	 * @param appID
	 * @return
	 */
	@Path("/visual/all")
	@GET
	public Response visualAll(@QueryParam("appid") String appID) {
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
					.entity("'appid' is not valid")
					.build();
		String topTopologyLoadingPath = appsRoot + "AppInfs" + File.separator + appID + File.separator + topologyInf;
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
					+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if( !tam.fullLoadWholeTopology() )
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		InfoData infoData = new InfoData();
		if(tam.wholeTopology.topologies != null){
			infoData.SubTopologies = new ArrayList<InfoData.STInfo>();
			for(int si = 0 ; si < tam.wholeTopology.topologies.size() ; si++){
				SubTopologyInfo curSTI = tam.wholeTopology.topologies.get(si);
				if(curSTI.subTopology.topologyName.equals("_ctrl"))
					continue;
				InfoData.STInfo newSTI = infoData.new STInfo();
				newSTI.Name = curSTI.topology;
				newSTI.CloudProvider = curSTI.cloudProvider;
				newSTI.DataCentre = curSTI.domain;
				newSTI.VMs = new ArrayList<InfoData.STInfo.VMInfo>();
				ArrayList<VM> vms = null;
				if( ( vms = curSTI.subTopology.getVMsinSubClass() ) != null){
					if(newSTI.VMs == null)
						newSTI.VMs = new ArrayList<InfoData.STInfo.VMInfo>();
					for(int vi = 0 ; vi<vms.size() ; vi++){
						VM curVM = vms.get(vi);
						InfoData.STInfo.VMInfo newVM = newSTI.new VMInfo();
						newVM.Name = curVM.name;
						newVM.CPU = curVM.CPU;
						newVM.MEM = curVM.Mem;
						newVM.PublicIP = curVM.publicAddress;
						newVM.OSType = curVM.OStype;
						newVM.Status = curSTI.status.trim().toLowerCase();
						
						if(curSTI.status.equalsIgnoreCase("running")){
							long hc = (curSTI.topology+"+"+curSTI.cloudProvider+"+"+curSTI.domain).hashCode();
							long max = 0xFFFFFF;
							String hexRaw = Integer.toHexString((int)(hc%max));
							newVM.Color = hexRaw.substring(hexRaw.length()-6);
						}else{
							newVM.Color = "000000";
						}
						newSTI.VMs.add(newVM);
					}
				}
				infoData.SubTopologies.add(newSTI);
			}
		}
		if(tam.wholeTopology.subnets != null){
			infoData.Subnets = new ArrayList<InfoData.SBInfo>();
			for(int si = 0 ; si < tam.wholeTopology.subnets.size() ; si++){
				Subnet curSubnet = tam.wholeTopology.subnets.get(si);
				InfoData.SBInfo newSubnet = infoData.new SBInfo();
				newSubnet.Name = curSubnet.name;
				newSubnet.Subnet = curSubnet.subnet;
				newSubnet.Netmask = curSubnet.netmask;
				newSubnet.Memebers = new ArrayList<InfoData.SBInfo.MBInfo>();
				if(curSubnet.members != null){
					for(int mi = 0 ; mi<curSubnet.members.size() ; mi++){
						InfoData.SBInfo.MBInfo newMember = newSubnet.new MBInfo();
						newMember.VMName = curSubnet.members.get(mi).absVMName;
						newMember.PrivateIP = curSubnet.members.get(mi).address;
						newSubnet.Memebers.add(newMember);
					}
				}
				infoData.Subnets.add(newSubnet);
			}
		}
		
		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonString = mapper.writeValueAsString(infoData);
			return Response.status(200)
					.entity(jsonString)
					.build();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return Response.status(520)
					.entity("JSON GEN Wrong: "+e.getMessage())
					.build();
		}

	}
	
	
	/**
	 * For the purpose of only visualizing the running infrastructure topology, the return value is a JSON String.
	 * @param appID
	 * @return
	 */
	@Path("/visual/running")
	@GET
	public Response visualRunning(@QueryParam("appid") String appID) {
		if(appID == null || appID.trim().equals(""))
			return Response.status(400)
					.entity("'appid' is not valid")
					.build();
		String topTopologyLoadingPath = appsRoot + "AppInfs" + File.separator + appID + File.separator + topologyInf;
		
		String appRootDir = appsRoot + "AppInfs" + File.separator + appID 
				+ File.separator;
		File appRootDirF = new File(appRootDir);
		if(!appRootDirF.exists())
			return Response.status(551)
					.entity("There is no application with AppID: " + appID)
					.build();
		
		TopologyAnalysisMain tam = new TopologyAnalysisMain(topTopologyLoadingPath);
		if( !tam.fullLoadWholeTopology() )
			return Response.status(520)
					.entity("Some problems with topology description files!\n"+topTopologyLoadingPath)
					.build();
		
	
		InfoData infoData = new InfoData();
		if(tam.wholeTopology.topologies != null){
			infoData.SubTopologies = new ArrayList<InfoData.STInfo>();
			for(int si = 0 ; si < tam.wholeTopology.topologies.size() ; si++){
				SubTopologyInfo curSTI = tam.wholeTopology.topologies.get(si);
				if( curSTI.subTopology.topologyName.equals("_ctrl") )
					continue;
				if( !curSTI.status.equalsIgnoreCase("running") )
					continue;
				InfoData.STInfo newSTI = infoData.new STInfo();
				newSTI.Name = curSTI.topology;
				newSTI.CloudProvider = curSTI.cloudProvider;
				newSTI.DataCentre = curSTI.domain;
				newSTI.VMs = new ArrayList<InfoData.STInfo.VMInfo>();
				ArrayList<VM> vms = null;
				if( ( vms = curSTI.subTopology.getVMsinSubClass() ) != null){
					if(newSTI.VMs == null)
						newSTI.VMs = new ArrayList<InfoData.STInfo.VMInfo>();
					for(int vi = 0 ; vi<vms.size() ; vi++){
						VM curVM = vms.get(vi);
						InfoData.STInfo.VMInfo newVM = newSTI.new VMInfo();
						newVM.Name = curVM.name;
						newVM.CPU = curVM.CPU;
						newVM.MEM = curVM.Mem;
						newVM.PublicIP = curVM.publicAddress;
						newVM.OSType = curVM.OStype;
						newVM.Status = "running";
						
						long hc = (curSTI.topology+"+"+curSTI.cloudProvider+"+"+curSTI.domain).hashCode();
						long max = 0xFFFFFF;
						String hexRaw = Integer.toHexString((int)(hc%max));
						newVM.Color = hexRaw.substring(hexRaw.length()-6);
						newSTI.VMs.add(newVM);
					}
				}
				infoData.SubTopologies.add(newSTI);
			}
		}
		if(tam.wholeTopology.subnets != null){
			infoData.Subnets = new ArrayList<InfoData.SBInfo>();
			for(int si = 0 ; si < tam.wholeTopology.subnets.size() ; si++){
				Subnet curSubnet = tam.wholeTopology.subnets.get(si);
				InfoData.SBInfo newSubnet = infoData.new SBInfo();
				newSubnet.Name = curSubnet.name;
				newSubnet.Subnet = curSubnet.subnet;
				newSubnet.Netmask = curSubnet.netmask;
				newSubnet.Memebers = new ArrayList<InfoData.SBInfo.MBInfo>();
				if(curSubnet.members != null){
					for(int mi = 0 ; mi<curSubnet.members.size() ; mi++){
						Member curMember = curSubnet.members.get(mi);
						VM curVM = tam.wholeTopology.VMIndex.get(curMember.absVMName);
						if(curVM == null){
							return Response.status(520)
									.entity("Cannot find "+curMember.absVMName)
									.build();
						}
						if( curVM.ponintBack2STI.status.equalsIgnoreCase("running") ){
							InfoData.SBInfo.MBInfo newMember = newSubnet.new MBInfo();
							newMember.VMName = curSubnet.members.get(mi).absVMName;
							newMember.PrivateIP = curSubnet.members.get(mi).address;
							newSubnet.Memebers.add(newMember);
						}
					}
				}
				if(newSubnet.Memebers.size() != 0)
					infoData.Subnets.add(newSubnet);
			}
			
			if(infoData.Subnets.size() == 0)
				infoData.Subnets = null;
		}
		
		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonString = mapper.writeValueAsString(infoData);
			return Response.status(200)
					.entity(jsonString)
					.build();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return Response.status(520)
					.entity("JSON GEN Wrong: "+e.getMessage())
					.build();
		}

	}
	
}
