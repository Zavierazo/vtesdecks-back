package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.SetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetRepository extends JpaRepository<SetEntity, Integer> {

    SetEntity findByAbbrev(String name);

    SetEntity findCompanyByFullName(String fullName);

}