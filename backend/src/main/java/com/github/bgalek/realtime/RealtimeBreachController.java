package com.github.bgalek.realtime;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.List;

@Controller
@RequestMapping("/api/realtime")
public class RealtimeBreachController {

    private final GuardrailBreachService breachService;
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeBreachController(GuardrailBreachService breachService, 
                                  SimpMessagingTemplate messagingTemplate) {
        this.breachService = breachService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * WebSocket message handler for listening to breaches
     */
    @MessageMapping("/subscribe-breaches")
    @SendTo("/topic/breaches")
    public GuardrailBreachService.GuardrailBreach handleSubscribe() {
        return null; // Client will receive updates via topic
    }

    /**
     * REST endpoint to get all breaches
     */
    @GetMapping("/breaches")
    @ResponseBody
    public List<GuardrailBreachService.GuardrailBreach> getAllBreaches() {
        return breachService.getAllBreaches();
    }

    /**
     * REST endpoint to get recent breaches (last N)
     */
    @GetMapping("/breaches/recent/{limit}")
    @ResponseBody
    public List<GuardrailBreachService.GuardrailBreach> getRecentBreaches(@PathVariable int limit) {
        return breachService.getRecentBreaches(limit);
    }

    /**
     * REST endpoint to get breaches for a specific user
     */
    @GetMapping("/breaches/user/{email}")
    @ResponseBody
    public List<GuardrailBreachService.GuardrailBreach> getBreachesForUser(@PathVariable String email) {
        return breachService.getBreachesForUser(email);
    }

    /**
     * REST endpoint to get breaches for a specific level
     */
    @GetMapping("/breaches/level/{level}")
    @ResponseBody
    public List<GuardrailBreachService.GuardrailBreach> getBreachesForLevel(@PathVariable int level) {
        return breachService.getBreachesForLevel(level);
    }

    /**
     * REST endpoint to get breach statistics
     */
    @GetMapping("/statistics")
    @ResponseBody
    public GuardrailBreachService.BreachStatistics getStatistics() {
        return breachService.getStatistics();
    }

    /**
     * Internal method to broadcast breach to all connected clients
     */
    public void broadcastBreach(GuardrailBreachService.GuardrailBreach breach) {
        messagingTemplate.convertAndSend("/topic/breaches", breach);
    }
}
