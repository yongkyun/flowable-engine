/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.engine.impl.cmd;

import java.io.Serializable;

import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.JobNotFoundException;
import org.activiti.engine.impl.cfg.TransactionState;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.jobexecutor.FailedJobListener;
import org.activiti.engine.impl.jobexecutor.JobExecutorContext;
import org.activiti.engine.impl.persistence.entity.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Tom Baeyens
 */
public class ExecuteJobsCmd implements Command<Object>, Serializable {

  private static final long serialVersionUID = 1L;

  private static Logger log = LoggerFactory.getLogger(ExecuteJobsCmd.class);
  
  protected String jobId;
 
  public ExecuteJobsCmd(String jobId) {
    this.jobId = jobId;
  }

  public Object execute(CommandContext commandContext) {
    
    if(jobId == null) {
      throw new ActivitiIllegalArgumentException("jobId is null");
    }
    
    if (log.isDebugEnabled()) {
      log.debug("Executing job {}", jobId);
    }
    
    JobEntity job = commandContext
      .getJobEntityManager()
      .findJobById(jobId);
    
    if (job == null) {
      throw new JobNotFoundException(jobId);
    }
    
    JobExecutorContext jobExecutorContext = Context.getJobExecutorContext();
    if(jobExecutorContext != null) { // if null, then we are not called by the job executor     
      jobExecutorContext.setCurrentJob(job);
    }
    
    try {
      job.execute(commandContext);
    } catch (RuntimeException exception) {
      // When transaction is rolled back, decrement retries
      CommandExecutor commandExecutor = Context
        .getProcessEngineConfiguration()
        .getCommandExecutorTxRequiresNew();
      
      commandContext.getTransactionContext().addTransactionListener(
        TransactionState.ROLLED_BACK, 
        new FailedJobListener(commandExecutor, jobId, exception));
       
      // throw the original exception to indicate the ExecuteJobCmd failed
      throw exception;
    } finally {
      if(jobExecutorContext != null) {
        jobExecutorContext.setCurrentJob(null);
      }
    }
    return null;
  }
  
  public String getJobId() {
		return jobId;
	}

}
