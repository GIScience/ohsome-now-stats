package org.heigit.ohsome.now.parquet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKTReader;

public class Countries {

  private Countries() {
    // utility class
  }


  public static List<PreparedGeometry> loadCountries(Path csv) throws IOException {
    var pattern = Pattern.compile("(\"[^\"]+\")|([^\",]+)");
    var wktReader = new WKTReader();
    try (var lines = Files.lines(csv)) {
      return lines.map(pattern::matcher).map(matcher -> {
            var row = new ArrayList<String>();
            while (matcher.find()) {
              row.add(matcher.group());
            }
            return row.toArray(String[]::new);
          })
          .map(row -> {
            var wkt = row[2];
            try {
              var geom = wktReader.read(wkt.substring(1, wkt.length() - 1));
              geom.setUserData(row[0]);
              return geom;
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          })
          .map(PreparedGeometryFactory::prepare)
          .collect(Collectors.toList());
    }
  }

  public static STRtree indexCountries(Path csv) throws IOException {
    var index = new STRtree();
    loadCountries(csv)
        .forEach(geometry -> index.insert(geometry.getGeometry().getEnvelopeInternal(), geometry));
    return index;
  }
}
