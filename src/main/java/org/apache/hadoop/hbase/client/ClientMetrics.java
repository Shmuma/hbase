package org.apache.hadoop.hbase.client;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.regionserver.ScannerStatistics;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.RunningJobContext;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientMetrics {
  public static enum Counters {
    PUT_CALLS,
    PUT_KVS,
    PUT_BYTES,
    PUT_MS,

    DELETE_CALLS,
    DELETE_MS,

    GET_CALLS,
    GET_KVS,
    GET_BYTES,
    GET_MS,

    SCAN_NEXT_MS,
    SCAN_NEXT_COUNT,
    SCAN_ROWS,
    SCAN_KVS,
    SCAN_BYTES,

    SCAN_DISK_BYTES,
    SCAN_READ_MS,
  }

  public static void trackPuts (int count, int kvs, long bytes, long ms) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.PUT_CALLS).increment(count);
    context.getCounter(Counters.PUT_KVS).increment(kvs);
    context.getCounter(Counters.PUT_BYTES).increment(bytes);
    context.getCounter(Counters.PUT_MS).increment(ms);
  }

  public static void trackDeletes (int count, long ms) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.DELETE_CALLS).increment(count);
    context.getCounter(Counters.DELETE_MS).increment(ms);
  }

  public static void trackGets (int count, int kvs, long bytes, long ms) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.GET_CALLS).increment(count);
    context.getCounter(Counters.GET_KVS).increment(kvs);
    context.getCounter(Counters.GET_BYTES).increment(bytes);
    context.getCounter(Counters.GET_MS).increment(ms);
  }

  public static void trackScanRow (int kvs, long bytes) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.SCAN_ROWS).increment(1);
    context.getCounter(Counters.SCAN_KVS).increment(kvs);
    context.getCounter(Counters.SCAN_BYTES).increment(bytes);
  }

  public static void trackScanNext (long ms)
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.SCAN_NEXT_COUNT).increment(1);
    context.getCounter(Counters.SCAN_NEXT_MS).increment(ms);
  }

  public static void trackScanIO (long disk, long readms)
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.SCAN_DISK_BYTES).increment(disk);
    context.getCounter(Counters.SCAN_READ_MS).increment(readms);
  }

  public static Result filterResult (Result res)
  {
    if (res == null)
      return res;
    if (!res.containsColumn(ScannerStatistics.stat_family, ScannerStatistics.stat_bytesDisk))
      return res;

    long disk = 0, readms = 0;
    boolean haveData = false;

    KeyValue[] vals = res.raw();
    List<KeyValue> out = new ArrayList<KeyValue>();
    for (int i = 0; i < vals.length; i++) {
      if (vals[i].matchingFamily(ScannerStatistics.stat_family)) {
        byte[] q = vals[i].getQualifier();
        if (Bytes.equals(q, ScannerStatistics.stat_bytesDisk)) {
          disk = Bytes.toLong(vals[i].getValue());
          haveData = true;
          continue;
        }
        if (Bytes.equals(q, ScannerStatistics.stat_readMS)) {
          readms = Bytes.toLong(vals[i].getValue());
          haveData = true;
          continue;
        }
      }
      else
        out.add(vals[i]);
    }

    if (haveData) {
      trackScanIO(disk, readms);
    }

    return new Result(out);
  }

  // Obtain raw size of Result object in bytes
  public static long resultSize (Result res)
  {
    long size = 0;

    if (res == null || res.isEmpty())
      return 0;

    if (res.getBytes() != null)
      return res.getBytes().getLength();

    if (res.raw() != null) {
      for (KeyValue kv : res.raw()) {
        size += kv.getLength();
      }
    }
    return size;
  }
}
