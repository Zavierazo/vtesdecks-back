package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    interface ArchonAccountLock {
        Integer getId();
        long getAuthVersion();
        String getVeknId();
    }

    // A scalar projection also reads the latest version when OpenEntityManagerInView is active.
    @Query(value = "SELECT id AS id, auth_version AS authVersion, vekn_id AS veknId FROM `user` WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<ArchonAccountLock> lockArchonAccount(Integer id);

    @Query("select u.veknId from UserEntity u where u.id = :id")
    String findVerifiedVeknId(Integer id);

    java.util.Optional<UserEntity> findByVeknId(String veknId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE `user` SET vekn_id = :veknId WHERE id = :id", nativeQuery = true)
    void updateVerifiedVeknId(Integer id, String veknId);

    /** Minimal identifiers and visibility flags; never load account secrets for sitemap generation. */
    interface SitemapUser {
        Integer getId();
        String getUsername();
        Boolean getWishlistPublicVisibility();
    }

    @Query("select u.id as id, u.username as username, u.wishlistPublicVisibility as wishlistPublicVisibility from UserEntity u")
    List<SitemapUser> findSitemapUsers();

    @Query("""
            select u.id from UserEntity u
            where u.wishlistPublicVisibility = true
            and exists (select w.id from WishlistCardEntity w where w.userId = u.id and w.number > 0)
            """)
    List<Integer> findNonEmptyPublicWishlistUserIdsForSitemap();

    UserEntity findByEmail(String email);

    UserEntity findByEmailIgnoreCase(String email);

    UserEntity findByUsername(String username);
    List<UserEntity> findByUsernameIn(java.util.Collection<String> usernames);

    List<UserEntity> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String usernamePart, String displayNamePart);

    @Query(value = "SELECT r.name FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id = :id", nativeQuery = true)
    List<String> selectRolesByUserId(Integer id);
}
