/*
Copyright (C) 2016-2020 Sysdig

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.sysdig.jenkins.plugins.sysdig.scanner;

import com.sysdig.jenkins.plugins.sysdig.BuildConfig;
import com.sysdig.jenkins.plugins.sysdig.client.*;
import hudson.AbortException;
import hudson.Launcher;
import hudson.model.TaskListener;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


public class BackendScanner extends Scanner {

  private SysdigSecureClient sysdigSecureClient;

  public BackendScanner(Launcher launcher, TaskListener listener, BuildConfig config) throws AbortException {
    super(launcher, listener, config);

    String sysdigToken = config.getSysdigToken();
    this.sysdigSecureClient = config.getEngineverify() ?
      SysdigSecureClientImpl.newClient(sysdigToken, config.getEngineurl()) :
      SysdigSecureClientImpl.newInsecureClient(sysdigToken, config.getEngineurl());
    this.sysdigSecureClient = new SysdigSecureClientImplWithRetries(this.sysdigSecureClient, 10);

  }

  @Override
  public ImageScanningSubmission scanImage(String imageTag, String dockerfile) throws AbortException {

    try {
      logger.logInfo(String.format("Submitting %s for analysis", imageTag));
      String imageDigest = sysdigSecureClient.submitImageForScanning(imageTag, dockerfile);
      logger.logInfo(String.format("Analysis request accepted, received image %s", imageDigest));
      return new ImageScanningSubmission(imageTag, imageDigest);
    } catch (Exception e) {
      logger.logError("Failed to add image(s) to sysdig-secure-engine due to an unexpected error", e);
      throw new AbortException("Failed to add image(s) to sysdig-secure-engine due to an unexpected error. Please refer to above logs for more information");
    }
  }

  @Override
  public JSONArray getGateResults(ImageScanningSubmission submission) throws AbortException {
    String tag = submission.getTag();
    String imageDigest = submission.getImageDigest();

    try {
      logger.logInfo(String.format("Waiting for analysis of %s with digest %s", tag, imageDigest));
      return sysdigSecureClient.retrieveImageScanningResults(tag, imageDigest);
    } catch (ImageScanningException e) {
      logger.logError("Unable to retrieve image scanning result for tag "+ tag + " digest " + imageDigest, e);
      throw new AbortException("Failed to retrieve policy evaluation due to an unexpected error. Please refer to above logs for more information");
    }
  }

  @Override
  public JSONObject getVulnsReport(ImageScanningSubmission submission) throws AbortException {
    String tag = submission.getTag();
    String imageDigest = submission.getImageDigest();

      try {
        logger.logInfo(String.format("Querying vulnerability listing of %s width digest %s", tag, imageDigest));
        return sysdigSecureClient.retrieveImageScanningVulnerabilities(imageDigest);
    } catch (ImageScanningException e) {
      logger.logError("Unable to retrieve vulnerabilities report for tag "+ tag + " digest " + imageDigest, e);
      throw new AbortException("Failed to retrieve vulnerabilities report due to an unexpected error. Please refer to above logs for more information");
    }
  }


}
