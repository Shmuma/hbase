package org.apache.hadoop.hbase.client;

import org.apache.hadoop.mapreduce.RunningJobContext;
import org.apache.hadoop.mapreduce.TaskInputOutputContext;

import java.io.IOException;

public class ClientMetrics {
  public static enum Counters {
    PUT_CALLS,
    PUT_KVS,
    PUT_BYTES,

    DELETE_CALLS,

    GET_CALLS,
    GET_KVS,
    GET_BYTES,
  }

  public static void trackPuts (int count, int kvs, long bytes) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.PUT_CALLS).increment(count);
    context.getCounter(Counters.PUT_KVS).increment(kvs);
    context.getCounter(Counters.PUT_BYTES).increment(bytes);
  }

  public static void trackDeletes (int count) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.DELETE_CALLS).increment(count);
  }

  public static void trackGets (int count, int kvs, long bytes) throws IOException
  {
    TaskInputOutputContext context = RunningJobContext.getContext ();
    if (context == null)
      return;
    context.getCounter(Counters.GET_CALLS).increment(count);
    context.getCounter(Counters.GET_KVS).increment(kvs);
    context.getCounter(Counters.GET_BYTES).increment(bytes);
  }
}
