package com.enterprise.iqk.config;

import com.enterprise.iqk.constants.SystemConstants;
import com.enterprise.iqk.tools.CourseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonConfiguration {

    @Bean
    public ChatClient chatClient(OpenAiChatModel model,ChatMemory chatMemory){
        return ChatClient
                .builder(model)
                .defaultOptions(ChatOptions.builder().model("qwen-omni-turbo").build())
                .defaultAdvisors(new SimpleLoggerAdvisor())//帮我记录日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))//增强器，MessageChatMemoryAdvisor：帮我们存储对话的上下文
                .defaultSystem("你是 LexScope Agent，面向民商法案例研判与法规检索。请基于用户问题给出专业、审慎、可追溯的回答；涉及法律依据时优先说明来源，不能替代执业律师正式法律意见。")
                .build();
    }

    @Bean
    public ChatClient serviceChatClient(OpenAiChatModel model,
                                        ChatMemory chatMemory,
                                        CourseTools courseTools){
        return ChatClient
                .builder(model)
                .defaultSystem(SystemConstants.CUSTOMER_SERVICE_SYSTEM)
                .defaultTools(courseTools)
                .defaultAdvisors(new SimpleLoggerAdvisor())//帮我记录日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))//增强器，MessageChatMemoryAdvisor：帮我们存储对话的上下文
                .build();
    }

    @Bean
    public ChatClient pdfChatClient(OpenAiChatModel model,
                                        ChatMemory chatMemory,
                                    VectorStore vectorStore){
        return ChatClient
                .builder(model)
                .defaultSystem("请严格按照民商法知识库上下文回答。若上下文没有匹配内容，请明确说明当前知识库未检索到依据，不要编造法条、案号或裁判结论。")
                .defaultAdvisors(new SimpleLoggerAdvisor())//帮我记录日志
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))//增强器，MessageChatMemoryAdvisor：帮我们存储对话的上下文
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                        .topK(2)
                        .similarityThreshold(0.5)//阈值
                        .build()))
                .build();
    }

    @Bean
    public ChatMemory chatMemory(MysqlChatMemory mysqlChatMemory){
        return mysqlChatMemory;
    }

}
