package org.magic;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.magic.common.card.Card;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guards: keeps the package layering that the refactoring
 * established. The intended dependency direction is
 *
 * <pre>drafts / feature services / admin  ->  org.magic.common</pre>
 *
 * where {@code common} holds the card model, game primitives, MongoDB and
 * external-API infrastructure and must stay free of feature knowledge.
 * Feature {@code api} payload packages must not reach into {@code app}
 * implementations, and the lobby service must keep dispatching through the
 * {@code DraftTypeHandler} SPI instead of referencing concrete draft modules.
 *
 * <p>Only production classes are analyzed: the rules scope the import to the
 * main classes root (the code source of {@link Card}).</p>
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = importMainClasses();

    private static JavaClasses importMainClasses() {
        try {
            Path mainClassesRoot = Paths.get(
                    Card.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ClassFileImporter().importPath(mainClassesRoot);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not resolve the main classes root", e);
        }
    }

    @Test
    void commonDoesNotDependOnFeaturePackages() {
        noClasses()
                .that().resideInAPackage("org.magic.common..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.magic.pyramidDraft..",
                        "org.magic.classicDraft..",
                        "org.magic.lobbyService..",
                        "org.magic.accountService..",
                        "org.magic.supportService..",
                        "org.magic.admin..",
                        "org.magic.burnDraft..")
                .because("org.magic.common is the shared core and must not know about features")
                .check(CLASSES);
    }

    @Test
    void apiPackagesDoNotDependOnApplicationPackages() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..app..")
                .because("wire payload types must not depend on service/worker implementations")
                .check(CLASSES);
    }

    @Test
    void lobbyServiceDoesNotDependOnDraftImplementations() {
        noClasses()
                .that().resideInAPackage("org.magic.lobbyService..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.magic.pyramidDraft..",
                        "org.magic.classicDraft..",
                        "org.magic.burnDraft..")
                .because("lobby dispatch must go through the DraftTypeHandler SPI")
                .check(CLASSES);
    }

    @Test
    void draftModulesDoNotDependOnEachOther() {
        noClasses()
                .that().resideInAPackage("org.magic.pyramidDraft..")
                .should().dependOnClassesThat().resideInAPackage("org.magic.classicDraft..")
                .because("draft formats are independent modules sharing only org.magic.common")
                .check(CLASSES);
        noClasses()
                .that().resideInAPackage("org.magic.classicDraft..")
                .should().dependOnClassesThat().resideInAPackage("org.magic.pyramidDraft..")
                .because("draft formats are independent modules sharing only org.magic.common")
                .check(CLASSES);
        noClasses()
                .that().resideInAPackage("org.magic.burnDraft..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.magic.pyramidDraft..",
                        "org.magic.classicDraft..")
                .because("draft formats are independent modules sharing only org.magic.common")
                .check(CLASSES);
        noClasses()
                .that().resideInAnyPackage("org.magic.pyramidDraft..", "org.magic.classicDraft..")
                .should().dependOnClassesThat().resideInAPackage("org.magic.burnDraft..")
                .because("draft formats are independent modules sharing only org.magic.common")
                .check(CLASSES);
    }
}
