/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.datanode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.hdfs.protocolPB.DatanodeProtocolClientSideTranslatorPB;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.common.IncorrectVersionException;
import org.apache.hadoop.hdfs.server.protocol.NamespaceInfo;
import org.apache.hadoop.test.GenericTestUtils;
import org.apache.hadoop.util.VersionInfo;
import org.junit.Before;
import org.junit.Test;

public class TestDatanodeRegister { 
  public static final Logger LOG =
      LoggerFactory.getLogger(TestDatanodeRegister.class);

  // Invalid address
  private static final InetSocketAddress INVALID_ADDR =
    new InetSocketAddress("127.0.0.1", 1);
  
  private BPServiceActor actor;
  NamespaceInfo fakeNsInfo;
  DNConf mockDnConf;
  
  @Before
  public void setUp() throws IOException {
    mockDnConf = mock(DNConf.class);
    doReturn(VersionInfo.getVersion()).when(mockDnConf).getMinimumNameNodeVersion();
    
    DataNode mockDN = mock(DataNode.class);
    doReturn(true).when(mockDN).shouldRun();
    doReturn(mockDnConf).when(mockDN).getDnConf();
    
    BPOfferService mockBPOS = mock(BPOfferService.class);
    doReturn(mockDN).when(mockBPOS).getDataNode();
    
    actor = new BPServiceActor("test", "test", INVALID_ADDR, null, mockBPOS);

    fakeNsInfo = mock(NamespaceInfo.class);
    // Return a a good software version.
    doReturn(VersionInfo.getVersion()).when(fakeNsInfo).getSoftwareVersion();
    // Return a good layout version for now.
    doReturn(HdfsServerConstants.NAMENODE_LAYOUT_VERSION).when(fakeNsInfo)
        .getLayoutVersion();
    
    DatanodeProtocolClientSideTranslatorPB fakeDnProt = 
        mock(DatanodeProtocolClientSideTranslatorPB.class);
    when(fakeDnProt.versionRequest()).thenReturn(fakeNsInfo);
    actor.setNameNode(fakeDnProt);
  }

  @Test
  public void testSoftwareVersionDifferences() throws Exception {
    // We expect no exception to be thrown when the software versions match.
    assertEquals(VersionInfo.getVersion(),
        actor.retrieveNamespaceInfo().getSoftwareVersion());
    
    // We expect no exception to be thrown when the min NN version is below the
    // reported NN version.
    doReturn("4.0.0").when(fakeNsInfo).getSoftwareVersion();
    doReturn("3.0.0").when(mockDnConf).getMinimumNameNodeVersion();
    assertEquals("4.0.0", actor.retrieveNamespaceInfo().getSoftwareVersion());
    
    // When the NN reports a version that's too low, throw an exception.
    doReturn("3.0.0").when(fakeNsInfo).getSoftwareVersion();
    doReturn("4.0.0").when(mockDnConf).getMinimumNameNodeVersion();
    try {
      actor.retrieveNamespaceInfo();
      fail("Should have thrown an exception for NN with too-low version");
    } catch (IncorrectVersionException ive) {
      GenericTestUtils.assertExceptionContains(
          "The reported NameNode version is too low", ive);
      LOG.info("Got expected exception", ive);
    }
  }
  
  @Test
  public void testDifferentLayoutVersions() throws Exception {
    // We expect no exceptions to be thrown when the layout versions match.
    assertEquals(HdfsServerConstants.NAMENODE_LAYOUT_VERSION,
        actor.retrieveNamespaceInfo().getLayoutVersion());
    
    // We expect an exception to be thrown when the NN reports a layout version
    // different from that of the DN.
    doReturn(HdfsServerConstants.NAMENODE_LAYOUT_VERSION * 1000).when(fakeNsInfo)
        .getLayoutVersion();
    try {
      actor.retrieveNamespaceInfo();
    } catch (IOException e) {
      fail("Should not fail to retrieve NS info from DN with different layout version");
    }
  }
}
