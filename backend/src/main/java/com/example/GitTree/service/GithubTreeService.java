package com.example.GitTree.service;

import com.example.GitTree.repo.GithubGraphqlRepo;
import org.springframework.stereotype.Service;

@Service
public class GithubTreeService {

    private final GithubGraphqlRepo githubGraphqlRepo;

    public GithubTreeService(GithubGraphqlRepo githubGraphqlRepo) {
        this.githubGraphqlRepo = githubGraphqlRepo;
    }

    public Object fetchTree(String owner, String repo, String branch) {
        return githubGraphqlRepo.getTree(owner, repo, branch);
    }
}
