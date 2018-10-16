package de.tum.bgu.msm.data.person;

import com.vividsolutions.jts.geom.Coordinate;
import de.tum.bgu.msm.data.household.Household;

public class PersonImpl implements Person{


    // Note: if attributes are edited, remember to edit attributes for inmigrants in \relocation\InOutMigration\setupInOutMigration.java and \relocation\InOutMigration\inmigrateHh.java as well
    //Attributes that must be initialized when one person is generated
    private final int id;
    private final Gender gender;
    private final Race race;

    private Occupation occupation;

    private int age;
    private int workplace;        // job ID
    private int schoolPlace = 0;  // ID of school
    private Coordinate schoolLocation;
    private int schoolZoneId;
    private int income;
    //Attributes that are generated by SILO
    private Household household;
    private PersonType type;
    private PersonRole role;
    //Attributes that could be additionally defined from the synthetic population. Remember to use "set"
    private int telework = 0;
    private int educationLevel = 0;
    private Nationality nationality = Nationality.GERMAN;
    private float travelTime = 0;
    private int jobTAZ = 0;
    private boolean driverLicense = false;
    private int schoolType = 0;

    PersonImpl(int id, int age, Gender gender, Race race, Occupation occupation, int workplace, int income) {
        this.id = id;
        this.age = age;
        this.gender = gender;
        this.race = race;
        this.occupation = occupation;
        this.workplace = workplace;
        this.income = income;
        setType();
    }

    private void setType () {
        this.type = PersonType.defineType(this);
    }

    @Override
    public void setHousehold(Household household) {
        this.household = household;
    }

    @Override
    public Household getHousehold() {
        return this.household;
    }

    @Override
    public void setRole(PersonRole pr) {
        this.role = pr;
    }

    @Override
    public void birthday() {
        this.age++;
        setType();
    }

    @Override
    public void setIncome (int newIncome) {
        this.income = newIncome;
        household.updateHouseholdType();
    }

    @Override
    public void setWorkplace(int newWorkplace) {
        this.workplace = newWorkplace;
    }

    @Override
    public void setOccupation(Occupation newOccupation) {
        this.occupation = newOccupation;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getAge() {
        return age;
    }

    @Override
    public Gender getGender() {
        return gender;
    }

    @Override
    public Race getRace() {
        return race;
    }

    @Override
    public Occupation getOccupation() {
        return occupation;
    }

    @Override
    public int getIncome() {
        return income;
    }

    @Override
    public PersonType getType() {
        return type;
    }

    @Override
    public PersonRole getRole() {
        return role;
    }

    @Override
    public int getWorkplace() {
        return workplace;
    }

    @Override
    public void setEducationLevel(int educationLevel) {
        this.educationLevel = educationLevel;
    }

    @Override
    public int getEducationLevel() {
        return educationLevel;
    }

    @Override
    public void setTelework(int telework) {
        this.telework = telework;
    }

    @Override
    public int getTelework() {
        return telework;
    }

    @Override
    public void setNationality(Nationality nationality) {
        this.nationality = nationality;
    }

    @Override
    public Nationality getNationality() {
        return nationality;
    }

    @Override
    public void setTravelTime(float travelTime){ this.travelTime = travelTime;}

    @Override
    public float getTravelTime() { return travelTime; }

    @Override
    public void setJobTAZ(int jobTAZ){ this.jobTAZ = jobTAZ;}

    @Override
    public int getJobTAZ() { return jobTAZ; }

    @Override
    public void setDriverLicense(boolean driverLicense){ this.driverLicense = driverLicense;}

    @Override
    public boolean hasDriverLicense() { return driverLicense; }

    @Override
    public void setSchoolType(int schoolType) {this.schoolType = schoolType; }

    @Override
    public int getSchoolType() {return schoolType;}

    @Override
    public void setSchoolPlace(int schoolPlace) {this.schoolPlace = schoolPlace;}

    @Override
    public int getSchoolPlace() {return schoolPlace;}

    @Override
    public Coordinate getSchoolLocation() {
        return schoolLocation;
    }

    @Override
    public int getSchoolZoneId() {
        return schoolZoneId;
    }

    @Override
    public void setSchoolCoordinate(Coordinate schoolLocation, int schoolZoneId) {
        this.schoolLocation = schoolLocation;
        this.schoolZoneId = schoolZoneId;
    }

    @Override
    public String toString() {
        return "Attributes of person " + id
                +"\nHousehold id         " + (household == null ? "null": household.getId())
                +"\nAge                  " + age
                +"\nGender               " + gender
                +"\nRole in household    " + role
                +"\nRace                 " + race
                +"\nOccupation           " + occupation
                +"\nWorkplace ID         " + workplace
                +"\nIncome               " + income
                +"\nPerson type          " + type.toString();
    }
}