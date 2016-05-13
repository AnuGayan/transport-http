/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.transport.http.netty.statistics.holders;

import org.wso2.carbon.metrics.manager.Timer;
import org.wso2.carbon.transport.http.netty.statistics.TimerHolder;

/**
 * Holder for source connection timer.
 */
public class SourceConnectionStaticsHolder implements MetricsStaticsHolder {

    private Timer connectionTimer = null;
    private Timer.Context connectionTimerContext = null;

    public SourceConnectionStaticsHolder(TimerHolder timerHolder) {
        connectionTimer = timerHolder.getSourceConnectionTimer();
    }

    @Override
    public void startTimer() {
        this.connectionTimerContext = this.connectionTimer.start();
    }

    @Override
    public void stopTimer() {
        if (this.connectionTimerContext != null) {
            this.connectionTimerContext.stop();
        }
    }

}
