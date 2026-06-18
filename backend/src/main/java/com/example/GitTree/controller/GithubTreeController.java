package com.example.GitTree.controller;

import com.example.GitTree.service.GithubTreeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
public class GithubTreeController {

    private final GithubTreeService githubTreeService;

    public GithubTreeController(GithubTreeService githubTreeService) {
        this.githubTreeService = githubTreeService;
    }

    @GetMapping("/tree")
    public Object getTree(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "main") String branch) {
        return githubTreeService.fetchTree(owner, repo, branch);
    }
}
