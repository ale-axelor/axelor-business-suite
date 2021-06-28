package com.axelor;

import com.axelor.app.AppModule;
import com.axelor.auth.AuthModule;
import com.axelor.db.JpaModule;
import com.google.inject.AbstractModule;

public class JpaTestModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new JpaModule("testUnit"));
    install(new AuthModule());
    install(new AppModule());
  }
}
