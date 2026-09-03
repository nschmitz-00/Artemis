import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UserStoryEffortFieldComponent } from 'app/programming/overview/user-story-effort/user-story-effort-field.component';

describe('UserStoryEffortFieldComponent', () => {
    let fixture: ComponentFixture<UserStoryEffortFieldComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(UserStoryEffortFieldComponent);
    });

    it('should show the reported value as hh:mm', () => {
        fixture.componentRef.setInput('value', 2.5);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('2:30h');
    });

    it('should show 0 as a real value rather than a placeholder', () => {
        fixture.componentRef.setInput('value', 0);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent.trim()).toBe('0:00h');
    });

    it('should show a placeholder for an unreported value', () => {
        fixture.componentRef.setInput('value', undefined);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).not.toContain('undefined');
    });
});
