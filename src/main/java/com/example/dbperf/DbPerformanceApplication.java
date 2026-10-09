package com.example.dbperf;

import com.example.dbperf.lessons.Lesson;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.List;

@SpringBootApplication
public class DbPerformanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbPerformanceApplication.class, args);
    }

    /**
     * Runs every lesson in order, or only the ones you ask for:
     *   mvn spring-boot:run -Dspring-boot.run.arguments="--lesson=2,4"
     */
    @Bean
    ApplicationRunner runner(List<Lesson> lessons, DataSeeder seeder) {
        return args -> {
            List<String> wanted = args.containsOption("lesson")
                    ? List.of(args.getOptionValues("lesson").get(0).split(","))
                    : List.of();

            seeder.seed();

            lessons.sort(AnnotationAwareOrderComparator.INSTANCE);
            for (Lesson lesson : lessons) {
                if (wanted.isEmpty() || wanted.contains(String.valueOf(lesson.number()))) {
                    System.out.println();
                    System.out.println("==============================================================");
                    System.out.println(" LESSON " + lesson.number() + ": " + lesson.title());
                    System.out.println("==============================================================");
                    lesson.run();
                }
            }
        };
    }
}
