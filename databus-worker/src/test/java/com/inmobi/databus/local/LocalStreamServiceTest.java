/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.inmobi.databus.local;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.CounterGroup;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.tools.mapred.CopyMapper;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.inmobi.databus.CheckpointProvider;
import com.inmobi.databus.Cluster;
import com.inmobi.databus.ClusterTest;
import com.inmobi.databus.DatabusConfig;
import com.inmobi.databus.DatabusConfigParser;
import com.inmobi.databus.DatabusConstants;
import com.inmobi.databus.DestinationStream;
import com.inmobi.databus.FSCheckpointProvider;
import com.inmobi.databus.SourceStream;
import com.inmobi.databus.TestMiniClusterUtil;
import com.inmobi.databus.local.LocalStreamService.CollectorPathFilter;
import com.inmobi.databus.utils.FileUtil;
import com.inmobi.messaging.Message;
import com.inmobi.messaging.publisher.MessagePublisher;
import com.inmobi.messaging.publisher.MessagePublisherFactory;
import com.inmobi.messaging.util.AuditUtil;

public class LocalStreamServiceTest extends TestMiniClusterUtil {
  private static Logger LOG = Logger.getLogger(LocalStreamServiceTest.class);
  private final static int NUMBER_OF_FILES = 9;
  public static final String FS_DEFAULT_NAME_KEY = "fs.default.name";
  public static final String SRC_FS_DEFAULT_NAME_KEY = "src.fs.default.name";

  Set<String> expectedResults = new LinkedHashSet<String>();
  Set<String> expectedTrashPaths = new LinkedHashSet<String>();
  Map<String, String> expectedCheckPointPaths = new HashMap<String, String>();

  @BeforeSuite
  public void setup() throws Exception {
    // clean up the test data if any thing is left in the previous runs
    cleanup();
    super.setup(2, 6, 1);
    System.setProperty(DatabusConstants.AUDIT_ENABLED_KEY, "true");
    createExpectedOutput();
  }

  @AfterSuite
  public void cleanup() throws Exception {
    super.cleanup();
  }

  private void createExpectedOutput() {
    createExpectedResults();
    createExpectedTrash();
    createExpectedCheckPointPaths();
  }

  private void createExpectedCheckPointPaths() {
    expectedCheckPointPaths.put("stream1collector1", "file8");
    expectedCheckPointPaths.put("stream1collector2", "file8");
    expectedCheckPointPaths.put("stream2collector1", "file8");
    expectedCheckPointPaths.put("stream2collector2", "file8");
  }

  private void createExpectedResults() {
    expectedResults.add("/databus/data/stream1/collector2/file1");
    expectedResults.add("/databus/data/stream1/collector2/file2");
    expectedResults.add("/databus/data/stream1/collector2/file3");
    expectedResults.add("/databus/data/stream1/collector2/file4");
    expectedResults.add("/databus/data/stream1/collector2/file5");
    expectedResults.add("/databus/data/stream1/collector2/file6");
    expectedResults.add("/databus/data/stream1/collector2/file7");
    expectedResults.add("/databus/data/stream1/collector2/file8");
    expectedResults.add("/databus/data/stream2/collector1/file1");
    expectedResults.add("/databus/data/stream2/collector1/file2");
    expectedResults.add("/databus/data/stream2/collector1/file3");
    expectedResults.add("/databus/data/stream2/collector1/file4");
    expectedResults.add("/databus/data/stream2/collector1/file5");
    expectedResults.add("/databus/data/stream2/collector1/file6");
    expectedResults.add("/databus/data/stream2/collector1/file7");
    expectedResults.add("/databus/data/stream2/collector1/file8");
    expectedResults.add("/databus/data/stream2/collector2/file1");
    expectedResults.add("/databus/data/stream2/collector2/file2");
    expectedResults.add("/databus/data/stream2/collector2/file3");
    expectedResults.add("/databus/data/stream2/collector2/file4");
    expectedResults.add("/databus/data/stream2/collector2/file5");
    expectedResults.add("/databus/data/stream2/collector2/file6");
    expectedResults.add("/databus/data/stream2/collector2/file7");
    expectedResults.add("/databus/data/stream2/collector2/file8");
    expectedResults.add("/databus/data/stream1/collector1/file1");
    expectedResults.add("/databus/data/stream1/collector1/file2");
    expectedResults.add("/databus/data/stream1/collector1/file3");
    expectedResults.add("/databus/data/stream1/collector1/file4");
    expectedResults.add("/databus/data/stream1/collector1/file5");
    expectedResults.add("/databus/data/stream1/collector1/file6");
    expectedResults.add("/databus/data/stream1/collector1/file7");
    expectedResults.add("/databus/data/stream1/collector1/file8");
  }

  private void createExpectedTrash() {
    expectedTrashPaths.add("/databus/data/stream2/collector2/file2");
    expectedTrashPaths.add("/databus/data/stream2/collector2/file1");
    expectedTrashPaths.add("/databus/data/stream1/collector1/file1");
    expectedTrashPaths.add("/databus/data/stream2/collector1/file1");
    expectedTrashPaths.add("/databus/data/stream2/collector1/file2");
    expectedTrashPaths.add("/databus/data/stream1/collector1/file2");
    expectedTrashPaths.add("/databus/data/stream1/collector2/file1");
    expectedTrashPaths.add("/databus/data/stream1/collector2/file2");
  }

  private void validateExpectedOutput(Set<String> results,
      Set<String> trashPaths, Map<String, String> checkPointPaths) {
    assert results.equals(expectedResults);
    assert trashPaths.equals(expectedTrashPaths);
    assert checkPointPaths.equals(expectedCheckPointPaths);
  }

  private void createMockForFileSystem(FileSystem fs, Cluster cluster)
      throws Exception {
    FileStatus[] files = createTestData(2, "/databus/data/stream", true);

    FileStatus[] stream1 = createTestData(2, "/databus/data/stream1/collector",
        true);

    FileStatus[] stream3 = createTestData(NUMBER_OF_FILES,
        "/databus/data/stream1/collector1/file", true);

    FileStatus[] stream4 = createTestData(NUMBER_OF_FILES,
        "/databus/data/stream1/collector2/file", true);

    FileStatus[] stream2 = createTestData(2, "/databus/data/stream2/collector",
        true);

    FileStatus[] stream5 = createTestData(NUMBER_OF_FILES,
        "/databus/data/stream2/collector1/file", true);

    FileStatus[] stream6 = createTestData(NUMBER_OF_FILES,
        "/databus/data/stream2/collector2/file", true);

    when(fs.getWorkingDirectory()).thenReturn(new Path("/tmp/"));
    when(fs.getUri()).thenReturn(new URI("localhost"));
    when(fs.listStatus(cluster.getDataDir())).thenReturn(files);
    when(fs.listStatus(new Path("/databus/data/stream1"))).thenReturn(stream1);

    when(
        fs.listStatus(new Path("/databus/data/stream1/collector1"),
            any(CollectorPathFilter.class))).thenReturn(stream3);
    when(fs.listStatus(new Path("/databus/data/stream2"))).thenReturn(stream2);
    when(
        fs.listStatus(new Path("/databus/data/stream1/collector2"),
            any(CollectorPathFilter.class))).thenReturn(stream4);
    when(
        fs.listStatus(new Path("/databus/data/stream2/collector1"),
            any(CollectorPathFilter.class))).thenReturn(stream5);
    when(
        fs.listStatus(new Path("/databus/data/stream2/collector2"),
            any(CollectorPathFilter.class))).thenReturn(stream6);

    Path file = mock(Path.class);
    when(file.makeQualified(any(FileSystem.class))).thenReturn(
        new Path("/databus/data/stream1/collector1/"));
  }

  private void testCreateListing() {
    try {
      Cluster cluster = ClusterTest.buildLocalCluster();
      FileSystem fs = mock(FileSystem.class);
      createMockForFileSystem(fs, cluster);

      Map<FileStatus, String> results = new TreeMap<FileStatus, java.lang.String>();
      Set<FileStatus> trashSet = new HashSet<FileStatus>();
      Map<String, FileStatus> checkpointPaths = new HashMap<String, FileStatus>();
      fs.delete(cluster.getDataDir(), true);
      FileStatus dataDir = new FileStatus(20, false, 3, 23823, 2438232,
          cluster.getDataDir());
      fs.delete(new Path(cluster.getRootDir() + "/databus-checkpoint"), true);

      Set<String> streamsToProcess = new HashSet<String>();
      streamsToProcess.add("stream1");
      streamsToProcess.add("stream2");
      TestLocalStreamService service = new TestLocalStreamService(null,
          cluster, null, new FSCheckpointProvider(cluster.getRootDir()
              + "/databus-checkpoint"), streamsToProcess);
      service.createListing(fs, dataDir, results, trashSet, checkpointPaths);

      Set<String> tmpResults = new LinkedHashSet<String>();
      // print the results
      for (FileStatus status : results.keySet()) {
        tmpResults.add(status.getPath().toString());
        LOG.debug("Results [" + status.getPath().toString() + "]");
      }

      // print the trash
      Iterator<FileStatus> it = trashSet.iterator();
      Set<String> tmpTrashPaths = new LinkedHashSet<String>();
      while (it.hasNext()) {
        FileStatus trashfile = it.next();
        tmpTrashPaths.add(trashfile.getPath().toString());
        LOG.debug("trash file [" + trashfile.getPath());
      }

      Map<String, String> tmpCheckPointPaths = new TreeMap<String, String>();
      // print checkPointPaths
      for (String key : checkpointPaths.keySet()) {
        tmpCheckPointPaths.put(key, checkpointPaths.get(key).getPath()
            .getName());
        LOG.debug("CheckPoint key [" + key + "] value ["
            + checkpointPaths.get(key).getPath().getName() + "]");
      }
      validateExpectedOutput(tmpResults, tmpTrashPaths, tmpCheckPointPaths);
      fs.delete(new Path(cluster.getRootDir() + "/databus-checkpoint"), true);
      fs.delete(cluster.getDataDir(), true);
      fs.close();
    } catch (Exception e) {
      LOG.debug("Error in running testCreateListing", e);
      assert false;
    }
  }

  private FileStatus[] createTestData(int count, String path, boolean useSuffix) {
    FileStatus[] files = new FileStatus[count];
    for (int i = 1; i <= count; i++) {
      files[i - 1] = new FileStatus(20, false, 3, 23232, 232323, new Path(path
          + ((useSuffix == true) ? (new Integer(i).toString()) : (""))));
    }
    return files;
  }

  private FileStatus[] createTestData(int count, String path) {
    return createTestData(count, path, false);
  }

  private DatabusConfig buildTestDatabusConfig() throws Exception {
    JobConf conf = super.CreateJobConf();
    return buildTestDatabusConfig(conf.get("mapred.job.tracker"),
        "file:///tmp", "databus", "48", "24");
  }

  public static DatabusConfig buildTestDatabusConfig(String jturl,
      String hdfsurl, String rootdir, String retentioninhours,
      String trashretentioninhours) throws Exception {

    Map<String, Integer> sourcestreams = new HashMap<String, Integer>();

    sourcestreams.put("cluster1", new Integer(retentioninhours));

    Map<String, SourceStream> streamMap = new HashMap<String, SourceStream>();
    streamMap.put("stream1", new SourceStream("stream1", sourcestreams));

    sourcestreams.clear();

    Map<String, DestinationStream> deststreamMap = new HashMap<String, DestinationStream>();
    deststreamMap.put("stream1",
        new DestinationStream("stream1", Integer.parseInt(retentioninhours),
            Boolean.TRUE));

    sourcestreams.clear();

    /*
     * sourcestreams.put("cluster2", new Integer(2)); streamMap.put("stream2",
     * new SourceStream("stream2", sourcestreams));
     */

    Set<String> sourcestreamnames = new HashSet<String>();

    for (Map.Entry<String, SourceStream> stream : streamMap.entrySet()) {
      sourcestreamnames.add(stream.getValue().getName());
    }
    Map<String, Cluster> clusterMap = new HashMap<String, Cluster>();

    clusterMap.put("cluster1", ClusterTest.buildLocalCluster(rootdir,
        "cluster1", hdfsurl, jturl, sourcestreamnames, deststreamMap));

    Map<String, String> defaults = new HashMap<String, String>();

    defaults.put(DatabusConfigParser.ROOTDIR, rootdir);
    defaults.put(DatabusConfigParser.RETENTION_IN_HOURS, retentioninhours);
    defaults.put(DatabusConfigParser.TRASH_RETENTION_IN_HOURS,
        trashretentioninhours);

    /*
     * clusterMap.put( "cluster2", ClusterTest.buildLocalCluster("cluster2",
     * "file:///tmp", conf.get("mapred.job.tracker")));
     */

    return new DatabusConfig(streamMap, clusterMap, defaults);
  }

  @Test
  public void testPopulateTrashPaths() throws Exception {
    FileStatus[] status = new FileStatus[10];
    String[] expectedstatus = new String[10];

    status[0] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster1/test1-2012-08-29-07-09_00000"));
    status[1] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster1/test1-2012-08-29-07-04_00000"));
    status[2] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test2/testcluster1/test2-2012-08-29-07-09_00003"));
    status[3] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster2/test1-2012-08-13-07-09_00000"));
    status[4] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster1/test1-2012-08-29-07-09_00009"));
    status[5] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster1/test1-2012-08-29-07-12_00000"));
    status[6] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster1/test1-2012-08-29-07-10_00000"));
    status[7] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test2/testcluster1/test2-2012-08-29-07-45_00000"));
    status[8] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster2/test1-2012-08-29-07-09_00078"));
    status[9] = new FileStatus(20, false, 3, 23823, 2438232, new Path(
        "/databus/data/test1/testcluster2/test1-2012-08-29-07-04_00034"));

    expectedstatus[0] = "/databus/data/test1/testcluster1/test1-2012-08-29-07-04_00000";
    expectedstatus[1] = "/databus/data/test1/testcluster1/test1-2012-08-29-07-09_00000";
    expectedstatus[2] = "/databus/data/test1/testcluster1/test1-2012-08-29-07-09_00009";
    expectedstatus[3] = "/databus/data/test1/testcluster1/test1-2012-08-29-07-10_00000";
    expectedstatus[4] = "/databus/data/test1/testcluster1/test1-2012-08-29-07-12_00000";

    expectedstatus[5] = "/databus/data/test1/testcluster2/test1-2012-08-13-07-09_00000";
    expectedstatus[6] = "/databus/data/test1/testcluster2/test1-2012-08-29-07-04_00034";
    expectedstatus[7] = "/databus/data/test1/testcluster2/test1-2012-08-29-07-09_00078";

    expectedstatus[8] = "/databus/data/test2/testcluster1/test2-2012-08-29-07-09_00003";
    expectedstatus[9] = "/databus/data/test2/testcluster1/test2-2012-08-29-07-45_00000";

    Set<FileStatus> trashSet = new HashSet<FileStatus>();
    for (int i = 0; i < 10; ++i) {
      trashSet.add(status[i]);
    }

    DatabusConfig databusConfig = buildTestDatabusConfig();
    Cluster cluster = ClusterTest.buildLocalCluster();
    Set<String> streamsToProcess = new HashSet<String>();
    streamsToProcess.addAll(databusConfig.getSourceStreams().keySet());
    TestLocalStreamService service = new TestLocalStreamService(
        databusConfig, cluster, null, new FSCheckpointProvider(
cluster.getCheckpointDir()),
        streamsToProcess);

    Map<Path, Path> trashCommitPaths = service
        .populateTrashCommitPaths(trashSet);

    Set<Path> srcPaths = trashCommitPaths.keySet();

    Iterator<Path> it = srcPaths.iterator();
    int i = 0;

    while (it.hasNext()) {
      String actualPath = it.next().toString();
      String expectedPath = expectedstatus[i];

      LOG.debug("Comparing Trash Paths Actual [" + actualPath + "] Expected ["
          + expectedPath + "]");
      Assert.assertEquals(actualPath, expectedPath);

      i++;
    }
  }

  @Test
  public void testMapReduce() throws Exception {
    LOG.info("Running LocalStreamIntegration for filename test-lss-databus.xml");
    testMapReduce("test-lss-databus.xml", 1);
  }

  @Test(groups = { "integration" })
  public void testMultipleStreamMapReduce() throws Exception {
    LOG.info("Running LocalStreamIntegration for filename test-lss-multiple-databus.xml");
    testMapReduce("test-lss-multiple-databus.xml", 1);
  }

  @Test(groups = { "integration" })
  public void testMultipleStreamMapReduceWithMultipleRuns() throws Exception {
    LOG.info("Running LocalStreamIntegration for filename test-lss-multiple-databus.xml, Running Twice");
    testMapReduce("test-lss-multiple-databus1.xml", 2);
  }

  private static class NullCheckPointProvider implements CheckpointProvider {

    @Override
    public byte[] read(String key) {
      return new byte[0];
    }

    @Override
    public void checkpoint(String key, byte[] checkpoint) {
    }

    @Override
    public void close() {
    }
  }

  @Test
  public void testCopyMapperImplMethod() throws Exception {
    DatabusConfigParser parser = new DatabusConfigParser(
        "test-lss-databus-s3n.xml");
    Set<String> streamsToProcess = new HashSet<String>();
    DatabusConfig config = parser.getConfig();
    streamsToProcess.addAll(config.getSourceStreams().keySet());

    Set<String> clustersToProcess = new HashSet<String>();
    Set<TestLocalStreamService> services = new HashSet<TestLocalStreamService>();
    for (SourceStream sStream : config.getSourceStreams().values()) {
      for (String cluster : sStream.getSourceClusters()) {
        clustersToProcess.add(cluster);
      }
    }

    for (String clusterName : clustersToProcess) {
      Cluster cluster = config.getClusters().get(clusterName);
      cluster.getHadoopConf().set("mapred.job.tracker",
          super.CreateJobConf().get("mapred.job.tracker"));
      TestLocalStreamService service = new TestLocalStreamService(config,
          cluster, null, new NullCheckPointProvider(), streamsToProcess);
      services.add(service);
    }

    for (TestLocalStreamService service : services) {
      Assert.assertEquals(service.getMapperClass(), S3NCopyMapper.class);
    }
  }

  @Test
  public void testWithOutClusterName() throws Exception {
    testClusterName("test-lss-databus.xml", null);
  }

  @Test
  public void testWithClusterName() throws Exception {
    testClusterName("test-lss-databus.xml", "testcluster2");
  }

  private void testClusterName(String configName, String currentClusterName)
      throws Exception {
    DatabusConfigParser parser = new DatabusConfigParser(configName);
    DatabusConfig config = parser.getConfig();
    Set<String> streamsToProcess = new HashSet<String>();
    streamsToProcess.addAll(config.getSourceStreams().keySet());
    Set<String> clustersToProcess = new HashSet<String>();
    Set<TestLocalStreamService> services = new HashSet<TestLocalStreamService>();
    Cluster currentCluster = null;
    for (SourceStream sStream : config.getSourceStreams().values()) {
      for (String cluster : sStream.getSourceClusters()) {
        clustersToProcess.add(cluster);
      }
    }
    if (currentClusterName != null) {
      currentCluster = config.getClusters().get(currentClusterName);
    }
    for (String clusterName : clustersToProcess) {
      Cluster cluster = config.getClusters().get(clusterName);
      cluster.getHadoopConf().set("mapred.job.tracker",
          super.CreateJobConf().get("mapred.job.tracker"));
      TestLocalStreamService service = new TestLocalStreamService(config,
          cluster, currentCluster, new NullCheckPointProvider(),
          streamsToProcess);
      services.add(service);
    }

    for (TestLocalStreamService service : services) {
      FileSystem fs = service.getFileSystem();
      service.preExecute();
      if (currentClusterName != null)
        Assert.assertEquals(service.getCurrentCluster().getName(),
            currentClusterName);
      // creating a job with empty input path
      Path tmpJobInputPath = new Path("/tmp/job/input/path");
      Map<FileStatus, String> fileListing = new TreeMap<FileStatus, String>();
      Set<FileStatus> trashSet = new HashSet<FileStatus>();
      // checkpointKey, CheckPointPath
      Map<String, FileStatus> checkpointPaths = new TreeMap<String, FileStatus>();
      service.createMRInput(tmpJobInputPath, fileListing, trashSet,
          checkpointPaths);
      Job testJobConf = service.createJob(tmpJobInputPath, 1000);
      testJobConf.waitForCompletion(true);

      int numberOfCountersPerFile = 0;
      long sumOfCounterValues = 0;
      Path outputCounterPath = new Path(new Path(service.getCluster().getTmpPath(),
          service.getName()), "counters");
      FileStatus[] statuses = fs.listStatus(outputCounterPath, new PathFilter() {
        public boolean accept(Path path) {
          return path.toString().contains("part");
        }
      });
      for (FileStatus fileSt : statuses) {
        Scanner scanner = new Scanner(fs.open(fileSt.getPath()));
        while (scanner.hasNext()) {
          String counterName = null;
          try {
            counterName = scanner.next();
            String tmp[] = counterName.split(CopyMapper.DELIMITER);
            Assert.assertEquals(3, tmp.length);
            Long numOfMsgs = scanner.nextLong();
            numberOfCountersPerFile++;
            sumOfCounterValues += numOfMsgs;
          } catch (Exception e) {
            LOG.error("Counters file has malformed line with counter name ="
                + counterName + "..skipping the line", e);
          }
        }
      }
      // Should have 2 counters for each file
      Assert.assertEquals(NUMBER_OF_FILES * 2, numberOfCountersPerFile);
      // sum of all counter values should be equal to total number of messages
      Assert.assertEquals(NUMBER_OF_FILES * 3, sumOfCounterValues);

      Assert.assertEquals(
          testJobConf.getConfiguration().get(FS_DEFAULT_NAME_KEY), service
              .getCurrentCluster().getHadoopConf().get(FS_DEFAULT_NAME_KEY));
      Assert.assertEquals(
          testJobConf.getConfiguration().get(SRC_FS_DEFAULT_NAME_KEY), service
              .getCluster().getHadoopConf().get(FS_DEFAULT_NAME_KEY));
      if (currentCluster == null)
        Assert.assertEquals(
            testJobConf.getConfiguration().get(FS_DEFAULT_NAME_KEY),
            testJobConf.getConfiguration().get(SRC_FS_DEFAULT_NAME_KEY));
      service.getFileSystem().delete(
          new Path(service.getCluster().getRootDir()), true);
    }

  }

  private void testMapReduce(String fileName, int timesToRun) throws Exception {

    DatabusConfigParser parser = new DatabusConfigParser(fileName);
    DatabusConfig config = parser.getConfig();
    Set<String> streamsToProcess = new HashSet<String>();
    streamsToProcess.addAll(config.getSourceStreams().keySet());
    Set<String> clustersToProcess = new HashSet<String>();
    Set<TestLocalStreamService> services = new HashSet<TestLocalStreamService>();

    for (SourceStream sStream : config.getSourceStreams().values()) {
      for (String cluster : sStream.getSourceClusters()) {
        clustersToProcess.add(cluster);
      }
    }

    for (String clusterName : clustersToProcess) {
      Cluster cluster = config.getClusters().get(clusterName);
      cluster.getHadoopConf().set("mapred.job.tracker",
          super.CreateJobConf().get("mapred.job.tracker"));
      TestLocalStreamService service = new TestLocalStreamService(config,
          cluster, null, new FSCheckpointProvider(cluster.getCheckpointDir()),
          streamsToProcess);
      services.add(service);
      service.getFileSystem().delete(
          new Path(service.getCluster().getRootDir()), true);
    }

    for (TestLocalStreamService service : services) {
      for (int i = 0; i < timesToRun; ++i) {
        service.preExecute();
        // set BYTES_PER_MAPPER to a lower value for test
        service.setBytesPerMapper(100);
        service.execute();
        long finishTime = System.currentTimeMillis();
        service.postExecute();
        Thread.sleep(1000);
        /*
         * check for number of times local stream service should run and no need
         * of waiting if it is the last run of service
         */
        if (timesToRun > 1 && (i < (timesToRun - 1))) {
          long sleepTime = service.getMSecondsTillNextRun(finishTime);
          Thread.sleep(sleepTime);
        }
      }
      service.getFileSystem().delete(
          new Path(service.getCluster().getRootDir()), true);
    }
  }

  @Test
  public void testFileUtil() throws Exception {
    String streamName = "test1";
    Path rootDir = new Path("/tmp/localServiceTest/testcluster2/mergeservice");
    Path dataDir = new Path(rootDir, "data/test1/testcluster2");
    FileSystem fs = dataDir.getFileSystem(new Configuration());
    fs.mkdirs(dataDir);
    String filenameStr = new String(streamName + "-" +
        TestLocalStreamService.getDateAsYYYYMMDDHHmm(new Date()) + "_00001");
    Path src = new Path(dataDir, filenameStr);

    LOG.debug("Creating Test Data with filename [" + filenameStr + "]");
    FSDataOutputStream streamout = fs.create(src);
    String content = "Creating Test data for teststream";
    Message msg = new Message(content.getBytes());
    long currentTimestamp = new Date().getTime();
    AuditUtil.attachHeaders(msg, currentTimestamp);
    byte[] encodeMsg = Base64.encodeBase64(msg.getData().array());
    streamout.write(encodeMsg);
    streamout.write("\n".getBytes());
    streamout.write(encodeMsg);
    streamout.write("\n".getBytes());
    long nextMinuteTimeStamp = currentTimestamp + 60000;
    // Genearate a msg with different timestamp.  Default window period is 60sec
    AuditUtil.attachHeaders(msg, nextMinuteTimeStamp);
    encodeMsg = Base64.encodeBase64(msg.getData().array());
    streamout.write(encodeMsg);
    streamout.close();
    Map<Long, Long> received = new HashMap<Long, Long>();
    Path target = new Path(new Path(rootDir,
        "system/tmp/LocalStreamService_testcluster2_test1@/" +
        "job_local_0001/attempt_local_0001_m_000000_0/"), filenameStr + ".gz");
    FileUtil.gzip(src, target, new Configuration(), received);
    Assert.assertEquals(2, received.size());
    // current timestamp window = currentTimestamp - (currentTimestamp % 60000)
    Assert.assertTrue(
        2 == received.get(currentTimestamp - (currentTimestamp % 60000)));
    // next timestamp window = nextMinuteTimeStamp - (nextMinuteTimeStamp %60000)
    Assert.assertTrue(
        1 == received.get(nextMinuteTimeStamp - (nextMinuteTimeStamp %60000)));
    fs.delete(rootDir, true);
  }
}
