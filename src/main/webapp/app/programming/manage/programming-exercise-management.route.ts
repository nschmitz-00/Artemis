import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { repositorySubRoutes } from 'app/programming/shared/routes/programming-exercise-repository.route';
import {
    CodeEditorTutorAssessmentContainerComponent,
    canLeaveCodeEditorTutorAssessmentContainer,
} from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';

export const routes: Routes = [
    {
        // Milestone create/edit reuses the same programming-exercise update page as a normal exercise (see
        // ProgrammingExerciseUpdateComponent.isMilestoneMode), so the milestone's settings (language, package,
        // build config, static code analysis, ...) can be configured exactly like a programming exercise, minus
        // Problem Statement/Points/Assessment - those stay independently configured per UserStoryExercise.
        path: 'milestone-exercise-groups/new',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exerciseManagement.addModal.milestoneGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'milestone-exercise-groups/:exerciseId/edit',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exerciseManagement.addModal.milestoneGroup.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // The anchor MilestoneExercise owns the group's shared template/solution/test repositories and is the only
        // exercise of the group that ever runs a build, so it gets the same detail page a normal programming exercise
        // has (repository URIs, build plan links, template/solution build status). Declared after the 'new' and 'edit'
        // routes above, which would otherwise be swallowed by this route's ':exerciseId' segment.
        path: 'milestone-exercise-groups/:exerciseId',
        loadComponent: () => import('app/programming/manage/detail/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.exerciseVariantGroup.milestoneDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // jhi-code-button navigates to the repository view relatively (['.', 'repository', type], see
        // programming-repository-buttons-detail.component.html), so the milestone detail page needs its own copy of
        // the repository sub-routes below the programming-exercise ones.
        path: 'milestone-exercise-groups/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: 'milestone-exercise-groups/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },
    {
        // UserStory create reuses the same programming-exercise update page as a normal exercise (see
        // ProgrammingExerciseUpdateComponent.isUserStoryMode), minus everything the milestone group already owns
        // (language, package, build config, static code analysis, timeline, ...) - only title/short name/categories/
        // difficulty and Problem Statement/Points/Assessment are configured here.
        path: 'user-story-exercises/new',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exerciseManagement.addModal.userStory.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/new',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/edit',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import/:exerciseId',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import-from-file',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/import-from-sharing',
        loadComponent: () => import('app/programming/manage/update/programming-exercise-update.component').then((m) => m.ProgrammingExerciseUpdateComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId',
        loadComponent: () => import('app/programming/manage/detail/programming-exercise-detail.component').then((m) => m.ProgrammingExerciseDetailComponent),
        resolve: {
            programmingExercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/version-history',
        loadComponent: () =>
            import('app/programming/manage/version-history/programming-exercise-version-history.component').then((m) => m.ProgrammingExerciseVersionHistoryComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exercise.versionHistory.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/grading/:tab',
        loadComponent: () =>
            import('app/programming/manage/grading/configure/programming-exercise-configure-grading.component').then((m) => m.ProgrammingExerciseConfigureGradingComponent),
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/edit-build-plan',
        loadComponent: () => import('app/programming/manage/build-plan-editor/build-plan-editor.component').then((m) => m.BuildPlanEditorComponent),
        resolve: {
            exercise: ProgrammingExerciseResolve,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.programmingExercise.buildPlanEditor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType',
        children: repositorySubRoutes,
    },
    {
        path: 'programming-exercises/:exerciseId/repository/:repositoryType/:repositoryId',
        children: repositorySubRoutes,
    },
    {
        path: 'programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [canLeaveCodeEditorTutorAssessmentContainer],
    },
];
