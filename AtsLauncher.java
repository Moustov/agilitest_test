import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AtsLauncher {

	/**
	 * AtsLauncher is a Java class used as a simple script file with Java >= 14
	 * This script will try to update ATS tools and will launch ATS execution suites
	 */

	private static final String OS = System.getProperty("os.name").toLowerCase();

	private static final String ATS_RELEASES_SERVER = "https://actiontestscript.com/releases";
	private static final String ATS_TOOLS_SERVER = "https://actiontestscript.com/tools/versions.php";
	private static final String ATS_JENKINS_TOOLS = "userContent/tools/versions.csv";
	private static final String ATS_TOOLS_FOLDER = System.getProperty("user.home") + "/.actiontestscript/tools";

	private static final String TARGET = "target";
	private static final String ATS_OUTPUT = "ats-output";
	private static final String BUILD_PROPERTIES = "build.properties";

	private static final String LINUX = "linux";
	private static final String WINDOWS = "windows";
	private static final String MACOS = "macos";

	private static final String TGZ = "tgz";
	private static final String ZIP = "zip";

	private static final String ATS = "ats";
	private static final String JDK = "jdk";
	private static final String JASPER = "jasper";

	/**
	 * This script file will launch ATS suite tests using ATS tools components available on ActionTestScript server or on a local installation
	 * <p>
	 * Available options for launching ATS suites :
	 * <p>
	 * 'prepareMaven' : Prepare 'build.properties' file that maven can use to find ATS tools for ATS tests executions
	 * 'buildEnvironment' : Only try to get ATS tools path and create 'build.properties' file that can be used by Maven launch test process
	 * 'suiteXmlFiles' : Comma separated names of ATS suites xml files in 'exec' folder of current project, to be launched by this script
	 * 'atsReport' : Report details level
	 * 1 - Simple execution report
	 * 2 - Detailed execution report
	 * 3 - Detailed execution report with screen-shot
	 * 'validationReport' : Generate proof of functional execution with screen-shot
	 * 'atsListScripts' : List of ats scripts that can be launched using a temp suite execution
	 * 'tempSuiteName' : If 'atsListScripts' option is defined this option override default suite name ('tempSuite')
	 * 'disableSsl' : Disable trust certificat check when using ActionTestScript tools server
	 * 'atsToolsUrl' : Alternative url path to ActionTestScript tools server (the server have to send a list of ATS tools in a comma separated values data (name, version, folder_name, zip_archive_url).
	 * 'jenkinsUrl' : Url of a Jenkins server with saved ATS tools archives, tools will be available at [Jenkins_Url_Server]/userContent/tools using 'version.csv' files with names, versions and path of ATS tools
	 * 'reportsDirectory' (or 'output') : This is the output folder for all files generated during execution of ATS tests suites
	 * 'outbound' : By default, this script will try to contact ActionTestScript tools server.
	 * if this value is set to 'false', 'off' or '0', this script will try to find tools on local installation using following ordered methods :
	 * - 'atsToolsFolder' property in '.atsProjectProperties' file in current project folder
	 * - 'ATS_TOOLS' environment variable set on current machine
	 * - 'userprofile' folder directory : [userprofile]/.actiontestscript/tools
	 * <p>
	 * About '.atsProjectProperties' file in current project folder in xml format :
	 * - if tag 'atsToolsFolder' found : the value will define the local folder path of ATS tools
	 * - if tag 'atsToolsUrl' found : the standard ATS tools url server will be overwritten
	 * - if tag 'outbound' found : if the value is false, off or 0, no request will be send to get ATS tools
	 */

	private static String operatingSystem = LINUX;
	private static String archiveExtension = TGZ;
	private static String quotesInPath = "";

	private static String suiteFiles = "suite";
	private static String atsScripts = "";
	private static String tempSuiteName = "tempSuite";
	private static String reportLevel = "";
	private static String validationReport = "0";
	private static String output = TARGET + "/" + ATS_OUTPUT;

	private static String atsToolsFolderProperty = "atsToolsFolder";
	private static String atsToolsUrlProperty = "atsToolsUrl";
	private static String outboundProperty = "outbound";
	private static String disableSSLParam = "disableSSL";

	private static String htmlReportParam = "1";

	private static String atsToolsFolder = null;
	private static String atsToolsUrl = null;

	private static String atsHomePath = null;
	private static String atsVersion = null;
	private static String jdkHomePath = null;

	private static final List<String> trueList = Arrays.asList(new String[]{"on", "true", "1", "yes", "y"});
	private static final List<String> falseList = Arrays.asList(new String[]{"off", "false", "0", "no", "n"});

	private static final List<AtsToolEnvironment> atsToolsEnv =
			Arrays.asList(new AtsToolEnvironment[]{
					new AtsToolEnvironment(ATS),
					new AtsToolEnvironment(JASPER),
					new AtsToolEnvironment(JDK)});

	//------------------------------------------------------------------------------------------------------------
	// Main script execution
	//------------------------------------------------------------------------------------------------------------

	public static void main(String[] args) throws Exception, InterruptedException {

		if(OS.contains("win")) {
			operatingSystem = WINDOWS;
			archiveExtension = ZIP;
			quotesInPath = "\"";
		}

		printLog("Operating system detected : " + operatingSystem);

		final Integer javaVersion = Runtime.version().version().get(0);

		if (javaVersion < 14) {
			printLog("Java version " + javaVersion + " found, minimum version 14 is needed to execute this script !");
			System.exit(0);
		}
		printLog("Java version " + javaVersion + " found, start AtsLauncher execution ...");

		final File script = new File(AtsLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		final Path projectFolderPath = Paths.get(script.getParent().replace("%20", " "));
		final Path targetFolderPath = projectFolderPath.resolve(TARGET);

		printLog("AtsLauncher script in ATS project folder -> " + projectFolderPath.toString());

		String jenkinsToolsUrl = null;
		boolean buildEnvironment = false;
		boolean outboundTraffic = true;
		boolean disableSSLTrust = false;

		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		//-------------------------------------------------------------------------------------------------
		// Read pom.xml file
		//-------------------------------------------------------------------------------------------------

		atsVersion = getAtsVersion(dbf.newDocumentBuilder(), projectFolderPath.resolve("pom.xml").toAbsolutePath().toString());
		printLog("Ats library version defined in pom.xml -> " + atsVersion);

		//-------------------------------------------------------------------------------------------------
		// Read atsProjectProperties file
		//-------------------------------------------------------------------------------------------------

		final Path propFilePath = projectFolderPath.resolve(".atsProjectProperties").toAbsolutePath();
		try (InputStream is = new FileInputStream(propFilePath.toString())) {

			final Document doc = dbf.newDocumentBuilder().parse(is);

			if (doc.hasChildNodes()) {
				final Node root = doc.getChildNodes().item(0);
				if (root.hasChildNodes()) {
					final NodeList childs = root.getChildNodes();
					for (int i = 0; i < childs.getLength(); i++) {

						final String nodeName = childs.item(i).getNodeName();
						final String textContent = childs.item(i).getTextContent().trim();

						if (atsToolsFolderProperty.equalsIgnoreCase(nodeName)) {
							atsToolsFolder = textContent;
						} else if (atsToolsUrlProperty.equalsIgnoreCase(nodeName)) {
							atsToolsUrl = textContent;
						} else if (outboundProperty.equalsIgnoreCase(nodeName)) {
							outboundTraffic = falseList.indexOf(textContent.toLowerCase()) == -1;
						} else if (disableSSLParam.equalsIgnoreCase(nodeName)) {
							disableSSLTrust = trueList.indexOf(textContent.toLowerCase()) > -1;
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		//-------------------------------------------------------------------------------------------------
		// Read command line arguments
		//-------------------------------------------------------------------------------------------------

		for (int i = 0; i < args.length; i++) {

			final String allArgs = args[i].trim();
			final String firstArg = allArgs.toLowerCase();
			final int equalPos = firstArg.indexOf("=");

			if (equalPos == -1) {
				if ("buildenvironment".equals(firstArg) || "preparemaven".equals(firstArg)) {
					buildEnvironment = true;
				} else if ("disablessl".equals(firstArg)) {
					disableSSLTrust = true;
				}
			} else {
				final String argName = firstArg.substring(0, equalPos).replaceAll("\\-", "");
				final String argValue = allArgs.substring(equalPos + 1).trim();

				switch (argName) {
				case "suitexmlfiles":
					suiteFiles = argValue;
					break;
				case "atslistscripts":
					atsScripts = argValue;
					break;
				case "tempsuitename":
					if (argValue.length() > 0) {
						tempSuiteName = argValue;
					}
					break;
				case "preparemaven":
					buildEnvironment = trueList.indexOf(argValue.toLowerCase()) != -1;
					break;
				case "reportlevel":
				case "atsreport":
					reportLevel = argValue;
					break;
				case "validationreport":
					validationReport = argValue;
					break;
				case "htmlplayer":
					htmlReportParam = argValue;
					break;
				case "reportsdirectory":
				case "output":
					output = argValue;
					break;
				case "atstoolsurl":
					atsToolsUrl = argValue;
					break;
				case "atstoolsfolder":
					atsToolsFolder = argValue;
					break;
				case "outbound":
					outboundTraffic = falseList.indexOf(argValue.toLowerCase()) == -1;
					break;
				case "disablessl":
					disableSSLTrust = trueList.indexOf(argValue.toLowerCase()) > -1;
					break;
				case "jenkinsurl":
					jenkinsToolsUrl = argValue;
					if (!jenkinsToolsUrl.endsWith("/")) {
						jenkinsToolsUrl += "/";
					}
					jenkinsToolsUrl += ATS_JENKINS_TOOLS;
					break;
				}
			}
		}

		//-------------------------------------------------------------------------------------------------
		// Check if SSL certificates trust is disabled
		//-------------------------------------------------------------------------------------------------

		if (disableSSLTrust) {
			disableSSL();
		}

		//-------------------------------------------------------------------------------------------------
		// Check and delete output directories
		//-------------------------------------------------------------------------------------------------

		Path atsOutput = Paths.get(output);
		if (!atsOutput.isAbsolute()) {
			atsOutput = projectFolderPath.resolve(output);
		}

		deleteDirectory(targetFolderPath);
		deleteDirectory(atsOutput);

		deleteDirectory(projectFolderPath.resolve("test-output"));

		//-------------------------------------------------------------------------------------------------
		// Check list ATS scripts
		//-------------------------------------------------------------------------------------------------

		if (atsScripts != null && atsScripts.trim().length() > 0) {

			Files.createDirectories(Paths.get(TARGET));
			suiteFiles = TARGET + "/" + tempSuiteName + ".xml";

			final StringBuilder builder = new StringBuilder("<!DOCTYPE suite SYSTEM \"https://testng.org/testng-1.0.dtd\">\n");
			builder.append("<suite name=\"").append(tempSuiteName).append("\" verbose=\"0\">\n<test name=\"testMain\" preserve-order=\"true\">\n<classes>\n");

			final Stream<String> atsScriptsList = Arrays.stream(atsScripts.split(","));
			atsScriptsList.forEach(a -> addScriptToSuiteFile(builder, a));

			builder.append("</classes>\n</test></suite>");

			try (PrintWriter out = new PrintWriter(suiteFiles)) {
				out.println(builder.toString());
				out.close();
			}
		}

		//-------------------------------------------------------------------------------------------------
		// if ATS server url has not been set using default url
		//-------------------------------------------------------------------------------------------------

		if (atsToolsUrl == null) {
			atsToolsUrl = ATS_TOOLS_SERVER;
		}

		//-------------------------------------------------------------------------------------------------
		// try to get environment value
		//-------------------------------------------------------------------------------------------------

		if (atsToolsFolder == null) {
			atsToolsFolder = System.getenv("ATS_TOOLS");
		}

		if (reportLevel.isEmpty()) {
			String reportParam = System.getenv("ATS_REPORT");
			int tmp = 0;
			if (reportParam != null && !reportParam.isEmpty()) {
				try {
					tmp = Integer.parseInt(reportParam);
				}catch (NumberFormatException e){
					printLog("parameter can not be interpreted as number");
				}
			}
			if (tmp > 0 && tmp < 4) {
				reportLevel = Integer.toString(tmp);
			} else {
				reportLevel = "0";
			}
		}

		//-------------------------------------------------------------------------------------------------
		// if ats folder not defined using 'userprofile' home directory
		//-------------------------------------------------------------------------------------------------

		if (atsToolsFolder == null) {
			atsToolsFolder = ATS_TOOLS_FOLDER;
		}

		//-------------------------------------------------------------------------------------------------

		final List<String> envList = new ArrayList<String>();

		boolean serverFound = false;
		String useNetworkInfo = "";

		if (outboundTraffic) {
			useNetworkInfo = "ATS tools server is not reachable !";
			if (jenkinsToolsUrl != null) {
				serverFound = checkAtsToolsVersions(true, jenkinsToolsUrl);
			} else {
				serverFound = checkAtsToolsVersions(false, atsToolsUrl);
			}
		} else {
			useNetworkInfo = "Cannot connect to ATS tools server (outbound traffic has been turned off by user)";
		}

		if (!serverFound) {

			printLog(useNetworkInfo);

			atsToolsEnv.stream().forEach(e -> installAtsTool(e, envList, Paths.get(atsToolsFolder)));

			if (atsToolsEnv.size() != envList.size()) {
				printLog("ATS tools not found in folder -> " + atsToolsFolder);
				System.exit(0);
			}

		} else {
			atsToolsEnv.stream().forEach(e -> installAtsTool(e, envList));
		}

		if (buildEnvironment) {

			final Path p = projectFolderPath.resolve(BUILD_PROPERTIES);
			Files.deleteIfExists(p);

			Files.write(p, String.join("\n", envList).getBytes(), StandardOpenOption.CREATE);

			printLog("Build properties file created : " + p.toFile().getAbsolutePath());

		} else {

			Map<String, String> userEnv = System.getenv();
			for (String envName : userEnv.keySet()) {
				envList.add(0, envName + "=" + userEnv.get(envName));
			}

			final String[] envArray = envList.toArray(new String[envList.size()]);
			final File projectDirectoryFile = projectFolderPath.toFile();
			final Path generatedPath = targetFolderPath.resolve("generated");
			final File generatedSourceDir = generatedPath.toFile();
			final String generatedSourceDirPath = generatedSourceDir.getAbsolutePath();

			generatedSourceDir.mkdirs();

			printLog("Project directory -> " + projectDirectoryFile.getAbsolutePath());
			printLog("Generate java files -> " + generatedSourceDirPath);

			final FullLogConsumer logConsumer = new FullLogConsumer();

			final StringBuilder javaBuild =
					new StringBuilder(quotesInPath)
					.append(Paths.get(jdkHomePath).toAbsolutePath().toString())
					.append("/bin/java");

			execute(new StringBuilder(javaBuild)
					.append(quotesInPath).append(" -cp ").append(quotesInPath).append(atsHomePath).append(quotesInPath)
					.append("/libs/* com.ats.generator.Generator -prj ").append(quotesInPath)
					.append(projectFolderPath.toString()).append(quotesInPath)
					.append(" -dest ").append(quotesInPath)
					.append(targetFolderPath.toString())
					.append("/generated").append(quotesInPath).append(" -force -suites ")
					.append(suiteFiles),
					envArray,
					projectDirectoryFile,
					logConsumer,
					logConsumer);

			final ArrayList<String> files = listJavaClasses(generatedSourceDirPath.length() + 1, generatedSourceDir);

			final Path classFolder = targetFolderPath.resolve("classes").toAbsolutePath();
			final Path classFolderAssets = classFolder.resolve("assets");
			classFolderAssets.toFile().mkdirs();

			copyFolder(projectFolderPath.resolve("src").resolve("assets"), classFolderAssets);

			printLog("Compile classes to folder -> " + classFolder.toString());
			Files.write(generatedPath.resolve("JavaClasses.list"), String.join("\n", files).getBytes(), StandardOpenOption.CREATE);

			execute(new StringBuilder(javaBuild)
					.append("c").append(quotesInPath) // javac
					.append(" -cp ../../libs/*")
					.append(File.pathSeparator)
					.append(quotesInPath)
					.append(atsHomePath)
					.append("/libs/*").append(quotesInPath).append(" -d ").append(quotesInPath)
					.append(classFolder.toString())
					.append(quotesInPath)
					.append(" @JavaClasses.list"),
					envArray,
					generatedPath.toAbsolutePath().toFile(),
					logConsumer,
					logConsumer);

			printLog("Launch suite(s) execution -> " + suiteFiles);

			StringBuilder cmd = new StringBuilder(javaBuild)
					.append(quotesInPath)
					.append(" -Dats-report=").append(reportLevel)
					.append(" -Dvalidation-report=").append(validationReport)
					.append(" -Dhtmlplayer=").append(htmlReportParam)
					.append(" -cp ").append(quotesInPath).append(atsHomePath)
					.append("/libs/*").append(quotesInPath)
					.append(File.pathSeparator)
					.append(quotesInPath).append(targetFolderPath.toString()).append("/classes").append(quotesInPath)
					.append(File.pathSeparator)
					.append("libs/* org.testng.TestNG")
					.append(" -d ").append(quotesInPath).append(atsOutput.toString()).append(quotesInPath)
					.append(" ").append(quotesInPath).append(targetFolderPath.toString()).append("/suites.xml").append(quotesInPath);

			execute(cmd,
					envArray,
					projectDirectoryFile,
					logConsumer,
					new TestNGLogConsumer());
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Functions
	//------------------------------------------------------------------------------------------------------------

	private static void addScriptToSuiteFile(StringBuilder builder, String scriptName) {
		scriptName = scriptName.replaceAll("\\/", ".");
		if (scriptName.endsWith(".ats")) {
			scriptName = scriptName.substring(0, scriptName.length() - 4);
		}

		if (scriptName.startsWith(".")) {
			scriptName = scriptName.substring(1);
		}

		builder.append("<class name=\"").append(scriptName).append("\"/>\n");
	}

	private static Map<String, String[]> getServerToolsVersion(String serverUrl) {
		final Map<String, String[]> versions = new HashMap<String, String[]>();
		try {
			final URL url = new URL(serverUrl);

			HttpURLConnection.setFollowRedirects(false);
			final HttpURLConnection yc = (HttpURLConnection)url.openConnection();

			yc.setRequestMethod("GET");
			yc.setRequestProperty("Connection", "Keep-Alive");
			yc.setRequestProperty("Cache-Control", "no-cache");
			yc.setRequestProperty("User-Agent", "AtsLauncher-" + operatingSystem);

			yc.setUseCaches(false);
			yc.setDoOutput(true);

			final BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				String[] lineData = inputLine.split(",");
				versions.put(lineData[0], lineData);
			}

			in.close();

		} catch (IOException e) {
			printLog("AtsLauncher error -> " + e.getMessage());
		}

		return versions;
	}

	private static Boolean checkAtsToolsVersions(boolean localServer, String server) {

		printLog("Using ATS tools server -> " + server);

		final Map<String, String[]> versions = getServerToolsVersion(server);

		if (versions.size() < atsToolsEnv.size() && localServer) {
			printLog("Unable to get all ATS tools on this server -> " + server);
			versions.putAll(getServerToolsVersion(atsToolsUrl));
			if (versions.size() < atsToolsEnv.size()) {
				return false;
			}
		}

		for (AtsToolEnvironment t : atsToolsEnv) {
			final String[] toolData = versions.get(t.name);
			if (toolData != null) {
				final String folderName = toolData[2];
				t.folderName = folderName;

				final File toolFolder = Paths.get(atsToolsFolder).resolve(folderName).toFile();
				if (toolFolder.exists()) {
					t.folder = toolFolder.getAbsolutePath();
				} else {
					t.url = toolData[3];
				}
			} else {
				return false;
			}
		}

		return true;
	}

	private static void toolInstalled(AtsToolEnvironment tool, List<String> envList) {
		envList.add(tool.envName + "=" + tool.folder);
		printLog("Set environment variable [" + tool.envName + "] -> " + tool.folder);

		if (ATS.equals(tool.name)) {
			atsHomePath = tool.folder;
		} else if (JDK.equals(tool.name)) {
			jdkHomePath = tool.folder;
		}
	}

	private static void installAtsTool(AtsToolEnvironment tool, List<String> envList, Path toolsPath) {
		try (Stream<Path> stream = Files.walk(toolsPath, 1)) {
			final List<String> folders = stream
					.filter(file -> file != toolsPath && Files.isDirectory(file) && file.getFileName().toString().startsWith(tool.name))
					.map(Path::getFileName)
					.map(Path::toString)
					.collect(Collectors.toList());

			if (folders.size() > 0) {
				folders.sort(Collections.reverseOrder());

				tool.folder = toolsPath.resolve(folders.get(0)).toAbsolutePath().toString();
				toolInstalled(tool, envList);
			}

		} catch (Exception e) {
		}
	}

	private static void installAtsTool(AtsToolEnvironment tool, List<String> envList) {
		if (tool.folderName == null) {

			final File[] files = Paths.get(atsToolsFolder).toFile().listFiles();
			Arrays.sort(files, Comparator.comparingLong(File::lastModified));

			for (File f : files) {
				if (f.getName().startsWith(tool.name)) {
					tool.folderName = f.getName();
					tool.folder = f.getAbsolutePath();
					break;
				}
			}

		} else {
			try {
				final File tmpZipFile = Files.createTempDirectory("atsTool_").resolve(tool.folderName + "." + archiveExtension).toAbsolutePath().toFile();

				if (tmpZipFile.exists()) {
					tmpZipFile.delete();
				}

				final HttpURLConnection con = (HttpURLConnection) new URL(tool.url).openConnection();

				IntConsumer consume;
				final int fileLength = con.getContentLength();
				if (fileLength == -1) {
					consume = (p) -> {
						System.out.println("Download [" + tool.name + "] -> " + p + " Mo");
					};
				} else {
					consume = (p) -> {
						System.out.println("Download [" + tool.name + "] -> " + p + " %");
					};
				}

				ReadableConsumerByteChannel rcbc = new ReadableConsumerByteChannel(
						Channels.newChannel(con.getInputStream()),
						fileLength,
						consume);

				final FileOutputStream fosx = new FileOutputStream(tmpZipFile);
				fosx.getChannel().transferFrom(rcbc, 0, Long.MAX_VALUE);
				fosx.close();

				if(LINUX.equals(operatingSystem)) {

					final Path path = Paths.get(atsToolsFolder);
					Files.createDirectories(path);

					try {
						Runtime.getRuntime().exec("tar -xzf " + tmpZipFile.getAbsolutePath() + " -C " + path.toFile().getAbsolutePath()).waitFor();
					}catch(Exception e) {}

				}else {
					try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpZipFile))) {
						ZipEntry zipEntry = zis.getNextEntry();

						while (zipEntry != null) {
							final File newFile = newFile(Paths.get(atsToolsFolder).toFile(), zipEntry);
							if (newFile != null && !newFile.exists()) {
								if (zipEntry.isDirectory()) {

									if (!newFile.isDirectory() && !newFile.mkdirs()) {
										throw new IOException("Failed to create directory " + newFile);
									}

								} else {

									final File parent = newFile.getParentFile();
									if (!parent.isDirectory() && !parent.mkdirs()) {
										throw new IOException("Failed to create directory " + parent);
									}

									byte[] buffer = new byte[1024];
									final FileOutputStream fos = new FileOutputStream(newFile);
									int len;

									while ((len = zis.read(buffer)) > 0) {
										fos.write(buffer, 0, len);
									}
									fos.close();
								}
							}
							zis.closeEntry();
							zipEntry = zis.getNextEntry();
						}
						zis.close();
					}
				}

			} catch (IOException e) {
				//e.printStackTrace();
			}

			tool.folder = Paths.get(atsToolsFolder).resolve(tool.folderName).toFile().getAbsolutePath();
		}

		if (tool.folder == null) {
			throw new RuntimeException("ATS tool is not installed on this system -> " + tool.name);
		} else {
			toolInstalled(tool, envList);
		}
	}	

	private static String getAtsVersion(DocumentBuilder db, String pomFilePath) {
		try (InputStream is = new FileInputStream(pomFilePath)) {

			final Document doc = db.parse(is);

			final NodeList project = doc.getElementsByTagName("project");
			if (project.getLength() > 0) {
				final NodeList projectItems = project.item(0).getChildNodes();
				for (int i=0; i < projectItems.getLength(); i++) {
					if("dependencies".equals(projectItems.item(i).getNodeName())) {

						final NodeList dependencies = projectItems.item(i).getChildNodes();
						for (int j=0; j < dependencies.getLength(); j++) {

							String artifactId = null;
							String groupId = null;
							String version = null;

							final NodeList dependency = dependencies.item(j).getChildNodes();
							for (int k=0; k < dependency.getLength(); k++) {
								if("artifactId".equals(dependency.item(k).getNodeName())) {
									artifactId = dependency.item(k).getTextContent();
								}else if("groupId".equals(dependency.item(k).getNodeName())) {
									groupId = dependency.item(k).getTextContent();
								}else if("version".equals(dependency.item(k).getNodeName())) {
									version = dependency.item(k).getTextContent();
								}
							}

							if("com.actiontestscript".equals(groupId) && "ats-automated-testing".equals(artifactId)) {
								return version;
							}
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	//------------------------------------------------------------------------------------------------------------
	// Classes
	//------------------------------------------------------------------------------------------------------------

	private static class FullLogConsumer implements Consumer<String> {
		@Override
		public void accept(String s) {
			System.out.println(s);
		}
	}

	private static class TestNGLogConsumer implements Consumer<String> {
		@Override
		public void accept(String s) {
			System.out.println(
					s.replace("[TestNG]", "")
					.replace("[main] INFO org.testng.internal.Utils -", "[TestNG]")
					.replace("Warning: [org.testng.ITest]", "[TestNG] Warning :")
					.replace("[main] INFO org.testng.TestClass", "[TestNG]")
					);
		}
	}

	private static class StreamGobbler extends Thread {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
		}
	}

	private static class AtsToolEnvironment {

		public String name;
		public String envName;
		public String folder;
		public String folderName;

		public String url;

		public AtsToolEnvironment(String name) {
			this.name = name;
			this.envName = name.toUpperCase() + "_HOME";
		}
	}

	private static class ReadableConsumerByteChannel implements ReadableByteChannel {

		private final ReadableByteChannel rbc;
		private final IntConsumer onRead;
		private final int totalBytes;

		private int totalByteRead;

		private int currentPercent = 0;

		public ReadableConsumerByteChannel(ReadableByteChannel rbc, int totalBytes, IntConsumer onBytesRead) {
			this.rbc = rbc;
			this.totalBytes = totalBytes;
			this.onRead = onBytesRead;
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			int nRead = rbc.read(dst);
			notifyBytesRead(nRead);
			return nRead;
		}

		protected void notifyBytesRead(int nRead) {
			if (nRead <= 0) {
				return;
			}
			totalByteRead += nRead;

			if (totalBytes != -1) {
				int percent = (int) (((float) totalByteRead / totalBytes) * 100);
				if (percent % 5 == 0 && currentPercent != percent) {
					currentPercent = percent;
					onRead.accept(currentPercent);
				}
			} else if (totalByteRead % 10000 == 0) {
				onRead.accept(totalByteRead / 10000);
			}
		}

		@Override
		public boolean isOpen() {
			return rbc.isOpen();
		}

		@Override
		public void close() throws IOException {
			rbc.close();
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Utils
	//------------------------------------------------------------------------------------------------------------

	private static void printLog(String data) {
		System.out.println("[ATS-LAUNCHER] " + data);
	}

	private static void execute(StringBuilder command, String[] envp, File currentDir, Consumer<String> outputConsumer, Consumer<String> errorConsumer) throws IOException, InterruptedException {

		final Process p = Runtime.getRuntime().exec(command.toString(), envp, currentDir);

		new StreamGobbler(p.getErrorStream(), errorConsumer).start();
		new StreamGobbler(p.getInputStream(), outputConsumer).start();

		p.waitFor();
	}

	//------------------------------------------------------------------------------------------------------------
	// Files
	//------------------------------------------------------------------------------------------------------------

	private static void copyFolder(Path src, Path dest) throws IOException {
		try (Stream<Path> stream = Files.walk(src)) {
			stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
		}
	}

	private static void copy(Path source, Path dest) {
		try {
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static ArrayList<String> listJavaClasses(int subLen, File directory) {

		final ArrayList<String> list = new ArrayList<String>();
		final File[] fList = directory.listFiles();

		if (fList == null) {
			throw new RuntimeException("Directory list files return null value ! (" + directory.getAbsolutePath() + ")");
		} else {
			for (File file : fList) {
				if (file.isFile()) {
					if (file.getName().endsWith(".java")) {
						list.add(file.getAbsolutePath().substring(subLen).replaceAll("\\\\", "/"));
					}
				} else if (file.isDirectory()) {
					list.addAll(listJavaClasses(subLen, file));
				}
			}
		}

		return list;
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		final File destFile = new File(destinationDir, zipEntry.getName());
		if (destFile.getCanonicalPath().startsWith(destinationDir.getCanonicalPath() + File.separator)) {
			return destFile;
		}
		return null;
	}

	private static void deleteDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
					Files.delete(path);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path directory, IOException ioException) throws IOException {
					Files.delete(directory);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

	//------------------------------------------------------------------------------------------------------------
	// Tools
	//------------------------------------------------------------------------------------------------------------

	public static void disableSSL() throws NoSuchAlgorithmException, KeyManagementException {

		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {

			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		}};

		// Install the all-trusting trust manager

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());

		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier

		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}
}