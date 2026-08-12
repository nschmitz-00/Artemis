import { Component, computed, inject, input, output } from '@angular/core';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-medium-sidebar-card',
    templateUrl: './sidebar-card-medium.component.html',
    styleUrls: ['./sidebar-card-medium.component.scss'],
    imports: [NgClass, SidebarCardItemComponent, RouterLink, RouterLinkActive],
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

    /** How far a nested card (a user story below its milestone, see SidebarCardElement#indentLevel) is inset, in rem. */
    private static readonly INDENT_PER_LEVEL_REM = 1.25;

    /**
     * The inset of this card, as a rem value, or undefined for a top-level card. The card is a `col-12`, i.e. exactly as wide
     * as the sidebar, so the same amount has to come off its width - a bare margin would push its right edge out of the
     * sidebar instead of narrowing the card.
     */
    protected readonly indent = computed(() => {
        const indentLevel = this.sidebarItem()?.indentLevel;
        return indentLevel ? indentLevel * SidebarCardMediumComponent.INDENT_PER_LEVEL_REM : undefined;
    });

    onNonExamCardClicked() {
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
