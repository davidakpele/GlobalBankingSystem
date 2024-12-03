package com.example.deposit.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.example.deposit.models.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Query("SELECT n FROM Wallet n WHERE n.userId = :userId")
    Optional<Wallet> findByUserId(@Param("userId") Long userId);

    @Query("SELECT n FROM Wallet n WHERE n.userId = :userId")
    Wallet findWalletByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Wallet w WHERE w.userId IN :userIds")
    void deleteUserByIds(@Param("userIds") List<Long> userIds);
}