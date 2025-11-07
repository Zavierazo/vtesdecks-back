package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.LoadHistoryEntity;
import com.vtesdecks.jpa.repositories.LoadHistoryRepository;
import com.vtesdecks.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadHistoryService {
    private final LoadHistoryRepository loadHistoryRepository;

    public boolean isLoaded(String filePath) {
        LoadHistoryEntity loadHistory = loadHistoryRepository.findById(filePath).orElse(null);
        if (loadHistory != null) {
            String md5 = Utils.getMD5(getClass().getClassLoader(), filePath);
            if (loadHistory.getChecksum().equals(md5)) {
                log.info("File '{}' already loaded", filePath);
                return true;
            }
        }
        return false;
    }

    public void updateLoadedHistory(StopWatch stopWatch, String filePath) {
        LoadHistoryEntity loadHistory = loadHistoryRepository.findById(filePath).orElse(null);
        if (loadHistory == null) {
            loadHistory = new LoadHistoryEntity();
            loadHistory.setScript(filePath);
            loadHistory.setChecksum(Utils.getMD5(getClass().getClassLoader(), filePath));
            loadHistory.setExecutionTime(String.valueOf(stopWatch.lastTaskInfo().getTimeMillis()));
            loadHistoryRepository.saveAndFlush(loadHistory);
        } else {
            loadHistory.setChecksum(Utils.getMD5(getClass().getClassLoader(), filePath));
            loadHistory.setExecutionTime(String.valueOf(stopWatch.lastTaskInfo().getTimeMillis()));
            loadHistoryRepository.save(loadHistory);
        }
    }
}
