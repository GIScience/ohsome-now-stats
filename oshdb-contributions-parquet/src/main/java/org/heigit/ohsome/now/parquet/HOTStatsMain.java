package org.heigit.ohsome.now.parquet;

import java.util.concurrent.Callable;
import org.apache.ignite.Ignition;
import org.heigit.ohsome.oshdb.osm.OSMType;
import picocli.CommandLine.Command;

@Command(name = "hotstats", mixinStandardHelpOptions = true)
public class HOTStatsMain implements Callable<Integer> {

  @Override
  public Integer call()  {
    try (var ignite = Ignition.start("ohsome-heigit.xml")) {
      System.out.println();
      var cluster = ignite.cluster();
      System.out.println("cacheNames:");
      ignite.cacheNames().stream().sorted().forEach(cn -> System.out.println(" - " + cn));
      System.out.println();

      var job = new HOTStatsJob(ignite, OSMType.WAY);
      var compute = ignite.compute(cluster.forServers());
      compute.broadcast(job);
      //.forEach(System.out::println);
      return 0;
    }
  }

  public static void main(String[] args) throws Exception {
    var main = new HOTStatsMain();
    main.call();
  }
}
