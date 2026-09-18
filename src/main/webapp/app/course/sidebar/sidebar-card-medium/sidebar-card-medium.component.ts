import { Component, computed, inject, input, output } from '@angular/core';
import { DifficultyLevel, getExerciseUrlSegmentOrEmpty } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, isActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-medium-sidebar-card',
    templateUrl: './sidebar-card-medium.component.html',
    styleUrls: ['./sidebar-card-medium.component.scss'],
    imports: [NgClass, SidebarCardItemComponent, RouterLink],
})
export class SidebarCardMediumComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    protected readonly DifficultyLevel = DifficultyLevel;

    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly pageChange = output<string | number>();
    /** Key used for grouping or categorizing sidebar items */
    readonly groupKey = input<string>();

    /**
     * True when this card heads a connected variant group. The card styles itself (see `.group-header` in the SCSS) so
     * the accordion need not reach into its markup.
     */
    protected readonly isConnectedGroupHeader = computed<boolean>(() => {
        const item = this.sidebarItem();
        return !!item.groupedItems?.length && !!item.groupConnected;
    });

    /**
     * Whether this card's item is the one currently open, i.e. whether it draws the selected highlight.
     *
     * `routerLinkActive` on the card's own link is not enough: as soon as an exercise has a participation, the details
     * page redirects to the type-prefixed participation route (`…/exercises/programming-exercises/5/code-editor/42`,
     * see `participationChildRouteSegments`), which is not a prefix of this card's link (`…/exercises/5`) — so the
     * highlight dropped exactly when the student started working. Starting a milestone provisions a participation for
     * every user story of its group at once, which is when it became impossible to miss. Both URL shapes are matched
     * here; the plain one still has to match on its own so a deep link to an unstarted exercise keeps working.
     *
     * The type-prefixed shape is built from the exercise's own type rather than from a wildcard segment, so a group
     * detail URL (`…/exercises/group/7`) cannot light up the unrelated exercise that happens to have id 7 — group and
     * exercise ids come from independent sequences.
     */
    protected readonly isItemActive = computed<boolean>(() => {
        const item = this.sidebarItem();
        if (this.matchesRoute(item.targetComponentSubRoute, item.id)) {
            return true;
        }
        const exerciseType = item.exercise?.type;
        return !!exerciseType && this.matchesRoute(getExerciseUrlSegmentOrEmpty(exerciseType), item.id);
    });

    /** Whether the current URL is, or sits below, `./[subRoute/]id` — the same subset match `routerLinkActive` does. */
    private matchesRoute(subRoute: string | undefined, id: string | number | undefined): boolean {
        if (id === undefined || id === '') {
            return false;
        }
        const tree = this.router.createUrlTree(subRoute ? ['./', subRoute, id] : ['./', id], { relativeTo: this.route });
        // `isActive` hands back a computed signal over the router state, so reading it here is what keeps
        // `isItemActive` in step with navigation - no separate event subscription is needed.
        return isActive(tree, this.router, { paths: 'subset', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored' })();
    }

    onNonExamCardClicked() {
        if (this.sidebarItem().groupedItems?.length) {
            this.storeTargetComponentSubRoute();
            return;
        }
        this.storeTargetComponentSubRoute();
        if (this.itemSelected()) {
            this.refreshChildComponent();
        }
    }

    storeTargetComponentSubRoute() {
        const targetComponentSubRoute = this.sidebarItem().targetComponentSubRoute;
        const sidebarItemId = this.sidebarItem().id;
        const targetComponentRoute = targetComponentSubRoute ? targetComponentSubRoute + '/' + sidebarItemId : sidebarItemId;
        this.sidebarEventService.emitSidebarCardEvent(targetComponentRoute);
    }

    refreshChildComponent(): void {
        const targetComponentSubRoute = this.sidebarItem()?.targetComponentSubRoute;
        const itemId = this.sidebarItem()?.id;
        const pathSegments = targetComponentSubRoute ? ['./', targetComponentSubRoute, itemId] : ['./', itemId];
        void this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            void this.router.navigate(pathSegments, { relativeTo: this.route });
        });
    }

    onExamCardClicked() {
        this.pageChange.emit(this.sidebarItem().id);
    }
}
