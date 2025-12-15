package com.my.blog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.my.blog.model.Board;

public interface BoardRepository extends JpaRepository<Board, Integer> {

}
