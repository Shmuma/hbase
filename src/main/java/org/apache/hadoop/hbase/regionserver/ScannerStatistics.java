package org.apache.hadoop.hbase.regionserver;


import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.ArrayList;
import java.util.List;

// Holds resource usage counters for a giver scan
public class ScannerStatistics {
  public static byte[] stat_family = Bytes.toBytes("__stat_v1");

  public static byte[] stat_bytesDisk = Bytes.toBytes("disk");
  public static byte[] stat_readMS = Bytes.toBytes("readms");

  private long scanBytesDisk, scanReadMS;

  public ScannerStatistics ()
  {
    reset();
  }


  public void reset ()
  {
    scanReadMS = scanBytesDisk = 0;
  }


  public void join (ScannerStatistics stat)
  {
    if (stat != null) {
      scanBytesDisk  += stat.scanBytesDisk;
      scanReadMS += stat.scanReadMS;
    }
  }


  public List<KeyValue> makeKeyValues (byte[] row)
  {
    List<KeyValue> res = new ArrayList<KeyValue>();
    res.add(new KeyValue(row, stat_family, stat_bytesDisk, Bytes.toBytes(scanBytesDisk)));
    res.add(new KeyValue(row, stat_family, stat_readMS, Bytes.toBytes(scanReadMS)));
    return res;
  }

  public void trackDiskBytes (long bytes, long ms)
  {
    scanBytesDisk += bytes;
    scanReadMS += ms;
  }

  public String toString ()
  {
    return "disk = " + Long.toString(scanBytesDisk) + ", readms = " + Long.toString(scanReadMS);
  }
}
