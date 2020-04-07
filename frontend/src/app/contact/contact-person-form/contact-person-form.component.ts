import { ContactPersonDto } from './../../models/contact-person';
import { Component, OnInit, Input, Output, EventEmitter, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { FormGroup, FormBuilder, FormControl, Validators } from '@angular/forms';
import { SubSink } from 'subsink';
import { ApiService } from 'src/app/services/api.service';
import { SnackbarService } from 'src/app/services/snackbar.service';

@Component({
  selector: 'app-contact-person-form',
  templateUrl: './contact-person-form.component.html',
  styleUrls: ['./contact-person-form.component.scss']
})
export class ContactPersonFormComponent implements OnInit, OnDestroy {
  @Input() contactPerson: ContactPersonDto;
  @Output() contactCreated = new EventEmitter<ContactPersonDto>();
  @Output() contactModified = new EventEmitter<any>();
  @Output() cancelled = new EventEmitter<any>();
  @Output() dirty = new EventEmitter<boolean>();
  formGroup: FormGroup;
  private subs = new SubSink();
  showIdentificationHintField = false;

  constructor(
    private formBuilder: FormBuilder,
    private apiService: ApiService,
    private snackbarService: SnackbarService,

  ) { }

  ngOnInit() {
    this.buildForm();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  get isNew() {
    return (!this.contactPerson.id);
  }

  buildForm() {
    this.formGroup = this.formBuilder.group(
      {
        firstname: new FormControl(this.contactPerson.firstname),
        surename: new FormControl(this.contactPerson.surename),
        email: new FormControl(this.contactPerson.email, [Validators.email]),
        phone: new FormControl(this.contactPerson.phone, [Validators.minLength(5), Validators.maxLength(15)]),
        mobilePhone: new FormControl(this.contactPerson.mobilePhone, [Validators.minLength(5), Validators.maxLength(15)]),
        street: new FormControl(this.contactPerson.street),
        houseNumber: new FormControl(this.contactPerson.houseNumber, [Validators.maxLength(6)]),
        zipCode: new FormControl(this.contactPerson.zipCode, [Validators.minLength(5), Validators.maxLength(5)]),
        city: new FormControl(this.contactPerson.city),
        identificationHint: new FormControl(this.contactPerson.identificationHint),
        isHealthStuff: new FormControl(this.contactPerson.isHealthStuff),
        isSenior: new FormControl(this.contactPerson.isSenior),
        hasPreExistingConditions: new FormControl(this.contactPerson.hasPreExistingConditions),
        remark: new FormControl(this.contactPerson.remark)
      }
    );
    this.formGroup.valueChanges.subscribe(_ => this.dirty.emit(true));
    if (this.contactPerson.identificationHint) {
      this.showIdentificationHintField = true;
    }
  }

  private isWayToContactSet(): boolean {
    return this.formGroup.controls.email.value
      || this.formGroup.controls.phone.value
      || this.formGroup.controls.mobilePhone.value
      || this.formGroup.controls.identificationHint.value;
  }

  onSubmit() {
    if (this.formGroup.valid) {

      if (this.isWayToContactSet()) {
        Object.assign(this.contactPerson, this.formGroup.value);

        if (this.isNew) {
          this.createContactPerson();
        } else {
          this.modifyContactPerson();
        }

      } else if (!this.showIdentificationHintField) {
        this.snackbarService.confirm('Bitte geben Sie mindestens eine Kontaktmöglichkeit oder Hinweise zur Identifikation ein');
        this.showIdentificationHintField = true;
      } else {
        this.snackbarService.confirm('Bitte geben Sie mindestens eine Kontaktmöglichkeit oder Hinweise zur Identifikation ein');
      }
    }
  }

  createContactPerson() {
    this.subs.add(this.apiService
      .createContactPerson(this.contactPerson)
      .subscribe(createdContactPerson => {
        this.snackbarService.success('Kontaktperson erfolgreich angelegt');
        this.formGroup.markAsPristine();
        this.contactCreated.emit(createdContactPerson);
        this.dirty.emit(false);
      }));
  }

  modifyContactPerson() {
    this.subs.add(this.apiService
      .modifyContactPerson(this.contactPerson)
      .subscribe(_ => {
        this.snackbarService.success('Kontaktperson erfolgreich aktualisiert');
        this.formGroup.markAsPristine();
        this.contactModified.emit();
        this.dirty.emit(false);
      }));
  }

  cancel() {
    this.cancelled.emit();
  }
}
