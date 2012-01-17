package com.inmobi.databus.local;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.util.ReflectionUtils;

public class CopyMapper extends Mapper<Text, Text, Text, Text> {

  private static final Log LOG = LogFactory.getLog(CopyMapper.class);

  @Override
  public void map(Text key, Text value, Context context) throws IOException,
      InterruptedException {
    Path src = new Path(key.toString());
    String dest = value.toString();
    String collector = src.getParent().getName();
    String category = src.getParent().getParent().getName();

    FileSystem fs = FileSystem.get(context.getConfiguration());
    Path target = getTempPath(context, src, category, collector);
    FSDataOutputStream out = fs.create(target);
    GzipCodec gzipCodec = (GzipCodec) ReflectionUtils.newInstance(
        GzipCodec.class, context.getConfiguration());
    OutputStream compressedOut = gzipCodec.createOutputStream(out);
    FSDataInputStream in = fs.open(src);
    byte[] bytes = new byte[256];
    while (in.read(bytes) != -1) {
      compressedOut.write(bytes);
    }
    in.close();
    compressedOut.close();

    // move to final destination
    fs.mkdirs(new Path(dest).makeQualified(fs));
    Path destPath = new Path(dest + File.separator + collector + "-"
        + src.getName() + ".gz");
    LOG.info("Renaming file " + target + " to " + destPath);
    fs.rename(target, destPath);

  }

  private Path getTempPath(Context context, Path src, String category,
      String collector) {
    Path tempPath = new Path(getTaskAttemptTmpDir(context), category + "-"
        + collector + "-" + src.getName() + ".gz");
    return tempPath;
  }

  public static Path getTaskAttemptTmpDir(Context context) {
    TaskAttemptID attemptId = context.getTaskAttemptID();
    return new Path(getJobTmpDir(context, attemptId.getJobID()),
        attemptId.toString());
  }

  public static Path getJobTmpDir(Context context, JobID jobId) {
    return new Path(
        new Path(context.getConfiguration().get("databus.tmp.path")),
        jobId.toString());
  }
}