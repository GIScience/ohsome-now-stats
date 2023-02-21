package org.heigit.ohsome.now.parquet.util;

import me.tongfei.progressbar.ProgressBarConsumer;

public class SysoutProgressConsumer implements ProgressBarConsumer {
  private final int maxLength;

  public SysoutProgressConsumer(int maxLength) {
    this.maxLength = maxLength;
  }

  @Override
  public int getMaxRenderedLength() {
    return maxLength;
  }

  @Override
  public void accept(String rendered) {
    System.out.println(rendered);
  }

  @Override
  public void close() {
    // no/op
  }
}
