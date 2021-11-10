package com.irondev.ipldashboard.data;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;

import com.irondev.ipldashboard.model.Team;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;

@Component
@Log
public class JobCompletionNotificationListener extends JobExecutionListenerSupport {

  private final EntityManager em;

  @Autowired
  public JobCompletionNotificationListener(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public void afterJob(JobExecution jobExecution) {
    if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("!!! JOB FINISHED! Time to verify the results");

      Map<String, Team> teamData = new HashMap<>();

      em.createQuery("select m.team1, count(*) from Match m group by m.team1", Object[].class)
      .getResultList()
      .stream()
      .map(e -> new Team((String) e[0], (long) e[1]))
      .forEach(team -> teamData.put(team.getTeamName(), team));
  
      em.createQuery("select m.team2, count(*) from Match m group by m.team2", Object[].class)
      .getResultList()
      .forEach(e -> {
        Team team  = teamData.get((String) e[0]);
        if (team == null) {
          Team tempTeam = new Team((String) e[0], (long) e[1]);
          teamData.put(tempTeam.getTeamName(), tempTeam);
        }
        assert team != null;
        team.setTotalMatches(team.getTotalMatches() + (long) e[1]);
      });

      em.createQuery("select m.matchWinner, count(*) from Match m group by m.matchWinner", Object[].class)
              .getResultList()
              .forEach(e -> {
                Team tempTeam = teamData.get((String) e[0]);
                if (tempTeam != null) {
                  tempTeam.setTotalWins((long) e[1]);
                }
              });

      teamData.values().forEach(em::persist);

      teamData.values().forEach(System.out::println);
    }
  }
}