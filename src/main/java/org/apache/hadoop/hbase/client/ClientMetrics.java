package org.apache.hadoop.hbase.client;

import org.apache.hadoop.mapreduce.RunningJobContext;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

import java.io.IOException;

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
}
